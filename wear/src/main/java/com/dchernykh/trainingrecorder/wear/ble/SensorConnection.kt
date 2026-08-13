package com.dchernykh.trainingrecorder.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.dchernykh.trainingrecorder.core.ble.CscMeasurement
import com.dchernykh.trainingrecorder.core.ble.CyclingPowerMeasurement
import com.dchernykh.trainingrecorder.core.ble.HeartRateMeasurement
import com.dchernykh.trainingrecorder.core.ble.RevolutionRate
import com.dchernykh.trainingrecorder.core.ble.RscMeasurement
import com.dchernykh.trainingrecorder.core.field.SensorProfile
import com.dchernykh.trainingrecorder.core.sensor.SensorOrigin
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** What one connected sensor is currently reporting. */
data class SensorEvent(
    val profile: SensorProfile,
    val readings: Map<String, SensorReading>,
    val connected: Boolean = true,
)

/**
 * Maintains a GATT connection to one paired sensor and turns its notifications
 * into readings.
 *
 * The parsing itself lives in the shared module, where it is unit-tested against
 * byte layouts from the specification; this class is only the transport. Keeping
 * that seam is what makes the fiddly part - flags, endianness, counter rollover -
 * testable without a strap in the room.
 */
class SensorConnection(
    private val context: Context,
    private val address: String,
    private val profile: SensorProfile,
) {
    // Cadence needs the previous sample to compute a rate, and the connection
    // itself is per-collection rather than per-instance: holding the GATT in a
    // field would let a second collector overwrite the first, leaking one
    // connection and closing the wrong one on teardown.
    private var previousCrankRevolutions: Long? = null
    private var previousCrankEventTime: Int? = null

    /**
     * Connects and emits until the collector goes away.
     *
     * `autoConnect` is true on purpose: a sensor that drops out of range mid-ride
     * is reconnected by the stack when it comes back, without the app polling for
     * it. The first connection is slower in exchange, which matters far less.
     */
    @SuppressLint("MissingPermission")
    fun events(): Flow<SensorEvent> =
        callbackFlow {
            val device =
                BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
                    ?: run {
                        close(IllegalStateException("no bluetooth adapter"))
                        return@callbackFlow
                    }
            val callback =
                object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int,
                    ) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else {
                            trySend(SensorEvent(profile, emptyMap(), connected = false))
                        }
                    }

                    override fun onServicesDiscovered(
                        gatt: BluetoothGatt,
                        status: Int,
                    ) {
                        val characteristic =
                            gatt
                                .getService(serviceUuid(profile))
                                ?.getCharacteristic(measurementUuid(profile)) ?: return
                        gatt.setCharacteristicNotification(characteristic, true)
                        // Notifications only start once the client configuration
                        // descriptor is written; setCharacteristicNotification
                        // alone is a local flag and silently produces nothing.
                        characteristic
                            .getDescriptor(CLIENT_CONFIG)
                            ?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }
                    }

                    @Deprecated("Kept for API levels below 33, where the typed overload does not exist")
                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                    ) {
                        @Suppress("DEPRECATION")
                        characteristic.value?.let { trySend(SensorEvent(profile, readingsFrom(it))) }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                    ) {
                        trySend(SensorEvent(profile, readingsFrom(value)))
                    }
                }
            val connection = device.connectGatt(context, true, callback)
            awaitClose { connection?.close() }
        }

    private fun readingsFrom(data: ByteArray): Map<String, SensorReading> {
        val now = System.currentTimeMillis()

        fun reading(value: Double) = SensorReading(value, SensorOrigin.EXTERNAL, now)

        val parsed: Map<String, SensorReading>? =
            when (profile) {
                SensorProfile.HEART_RATE ->
                    HeartRateMeasurement.parse(data)?.let {
                        mapOf("hr" to reading(it.heartRateBpm.toDouble()))
                    }
                SensorProfile.CYCLING_POWER ->
                    CyclingPowerMeasurement.parse(data)?.let { measurement ->
                        buildMap<String, SensorReading> {
                            put("power", reading(measurement.instantaneousPowerWatts.toDouble()))
                            measurement.pedalPowerBalancePercent?.let { put("power_balance", reading(it)) }
                            cadenceFrom(
                                measurement.cumulativeCrankRevolutions,
                                measurement.lastCrankEventTime,
                            )?.let { put("cadence", reading(it)) }
                        }
                    }
                SensorProfile.CYCLING_SPEED_CADENCE ->
                    CscMeasurement.parse(data)?.let { measurement ->
                        cadenceFrom(measurement.cumulativeCrankRevolutions, measurement.lastCrankEventTime)
                            ?.let { mapOf("cadence" to reading(it)) }
                    }
                SensorProfile.RUNNING_SPEED_CADENCE ->
                    RscMeasurement.parse(data)?.let {
                        mapOf(
                            "speed_current" to reading(it.speedMps),
                            "cadence" to reading(it.cadenceSpm.toDouble()),
                        )
                    }

                else -> null
            }
        return parsed.orEmpty()
    }

    /** Needs two samples, so the first notification after connecting yields nothing. */
    private fun cadenceFrom(
        revolutions: Int?,
        eventTime: Int?,
    ): Double? {
        if (revolutions == null || eventTime == null) return null
        val previousRevolutions = previousCrankRevolutions
        val previousTime = previousCrankEventTime
        previousCrankRevolutions = revolutions.toLong()
        previousCrankEventTime = eventTime
        if (previousRevolutions == null || previousTime == null) return null
        return RevolutionRate.rpm(previousRevolutions, previousTime, revolutions.toLong(), eventTime)
    }

    private companion object {
        /** Client Characteristic Configuration, the descriptor that arms notifications. */
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun uuid(assigned: String): UUID = UUID.fromString("0000$assigned-0000-1000-8000-00805f9b34fb")

        fun serviceUuid(profile: SensorProfile): UUID = uuid(requireNotNull(profile.serviceUuid))

        fun measurementUuid(profile: SensorProfile): UUID =
            when (profile) {
                SensorProfile.HEART_RATE -> uuid("2A37")
                SensorProfile.CYCLING_SPEED_CADENCE -> uuid("2A5B")
                SensorProfile.CYCLING_POWER -> uuid("2A63")
                SensorProfile.RUNNING_SPEED_CADENCE -> uuid("2A53")
                else -> uuid("2A37")
            }
    }
}
