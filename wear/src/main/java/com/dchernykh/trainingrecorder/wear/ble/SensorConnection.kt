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
    val address: String,
    val profiles: Set<SensorProfile>,
    val readings: Map<String, SensorReading>,
    val connected: Boolean = true,
    /**
     * True for the one event sent when the device's services have been read.
     *
     * It carries what the sensor turned out to be, which is not necessarily what
     * it advertised. Kept apart from a reading because the two mean different
     * things: this says the sensor *can* report something, a reading says it
     * just did - and only the second may take a field away from the watch.
     */
    val discovery: Boolean = false,
)

/**
 * Maintains a GATT connection to one paired sensor and turns its notifications
 * into readings.
 *
 * The parsing itself lives in the shared module, where it is unit-tested against
 * byte layouts from the specification; this class is only the transport. Keeping
 * that seam is what makes the fiddly part - flags, endianness, counter rollover -
 * testable without a strap in the room.
 *
 * A sensor is a set of profiles, not one. Chest straps routinely advertise heart
 * rate *and* running speed and cadence; a bike computer sensor may speak power
 * and cadence. Reading only one of them is how a heart-rate strap ends up
 * connected, described as a running sensor, and reporting no heart rate at all.
 *
 * Which profiles those are is decided here, from the device's own service table,
 * and not from what it said while advertising. An advertisement is a few dozen
 * bytes a sensor chooses what to put in - a Garmin HRM-Pro+ leads with running
 * cadence and does not have to mention heart rate at all - so trusting it means
 * subscribing to whichever service the strap happened to name and calling the
 * strap by that name afterwards. The service table is the sensor telling the
 * truth about itself, and reading it costs nothing: it has to be read to
 * subscribe to anything.
 */
class SensorConnection(
    private val context: Context,
    private val address: String,
) {
    /**
     * Cadence needs the previous sample to compute a rate, so the state is
     * per-collection rather than per-instance: two collections of the cold
     * [events] flow would otherwise clobber each other's baseline and compute a
     * rate across a gap. The same reasoning keeps the GATT handle local.
     */
    private class Counter {
        var revolutions: Long? = null
        var eventTime: Int? = null

        fun clear() {
            revolutions = null
            eventTime = null
        }
    }

    private class CrankBaseline {
        val crank = Counter()
        val wheel = Counter()

        fun clear() {
            crank.clear()
            wheel.clear()
        }
    }

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
            val baseline = CrankBaseline()
            // What is left to arm. Android's GATT queue holds one operation at a
            // time, so a second descriptor written before the first is
            // acknowledged is simply dropped - which would leave a two-profile
            // strap reporting only whichever characteristic happened to win.
            val pending = ArrayDeque<SensorProfile>()

            // Filled in once the services are read, and used from then on: what
            // this sensor actually offers.
            var found = emptySet<SensorProfile>()
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
                            // The counters are meaningless across a gap: rpm
                            // computed from a minutes-old sample against a timer
                            // that wraps every 64 seconds is exactly the bogus
                            // spike this class exists to avoid.
                            baseline.clear()
                            pending.clear()
                            trySend(SensorEvent(address, found, emptyMap(), connected = false))
                        }
                    }

                    override fun onServicesDiscovered(
                        gatt: BluetoothGatt,
                        status: Int,
                    ) {
                        pending.clear()
                        // Everything this build can read that the device actually
                        // carries. Not what it advertised: that is a marketing
                        // slot, this is the device's own service table.
                        found = supportedProfiles.filter { characteristicFor(gatt, it) != null }.toSet()
                        trySend(SensorEvent(address, found, emptyMap(), discovery = true))
                        found.forEach { pending.addLast(it) }
                        armNext(gatt)
                    }

                    override fun onDescriptorWrite(
                        gatt: BluetoothGatt,
                        descriptor: BluetoothGattDescriptor,
                        status: Int,
                    ) {
                        // The acknowledgement is the only safe moment to start
                        // the next one, whether or not this one succeeded - a
                        // refused descriptor must not strand the rest.
                        armNext(gatt)
                    }

                    /** Arms the next characteristic in the queue, if any. */
                    private fun armNext(gatt: BluetoothGatt) {
                        val next = pending.removeFirstOrNull() ?: return
                        val characteristic = characteristicFor(gatt, next) ?: return armNext(gatt)
                        gatt.setCharacteristicNotification(characteristic, true)
                        // Notifications only start once the client configuration
                        // descriptor is written; setCharacteristicNotification
                        // alone is a local flag and silently produces nothing.
                        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG) ?: return armNext(gatt)
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (!gatt.writeDescriptor(descriptor)) armNext(gatt)
                    }

                    @Deprecated("Kept for API levels below 33, where the typed overload does not exist")
                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                    ) {
                        @Suppress("DEPRECATION")
                        characteristic.value?.let { deliver(characteristic, it) }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                    ) = deliver(characteristic, value)

                    /** Which profile a notification belongs to decides how it parses. */
                    private fun deliver(
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                    ) {
                        val source = found.firstOrNull { measurementUuid(it) == characteristic.uuid } ?: return
                        trySend(SensorEvent(address, setOf(source), readingsFrom(source, value, baseline)))
                    }
                }
            val connection = device.connectGatt(context, true, callback)
            awaitClose {
                connection?.close()
                baseline.clear()
            }
        }

    private fun readingsFrom(
        profile: SensorProfile,
        data: ByteArray,
        baseline: CrankBaseline,
    ): Map<String, SensorReading> {
        val now = System.currentTimeMillis()

        fun reading(value: Double) = SensorReading(value, SensorOrigin.EXTERNAL, now)

        return when (profile) {
            SensorProfile.HEART_RATE -> heartRate(data, ::reading)
            SensorProfile.CYCLING_POWER -> cyclingPower(data, baseline, ::reading)
            SensorProfile.CYCLING_SPEED_CADENCE -> speedAndCadence(data, baseline, ::reading)
            SensorProfile.RUNNING_SPEED_CADENCE -> runningSpeed(data, ::reading)
            else -> emptyMap()
        }
    }

    private fun heartRate(
        data: ByteArray,
        reading: (Double) -> SensorReading,
    ): Map<String, SensorReading> =
        HeartRateMeasurement
            .parse(data)
            ?.let {
                mapOf("hr" to reading(it.heartRateBpm.toDouble()))
            }.orEmpty()

    private fun cyclingPower(
        data: ByteArray,
        baseline: CrankBaseline,
        reading: (Double) -> SensorReading,
    ): Map<String, SensorReading> {
        val measurement = CyclingPowerMeasurement.parse(data) ?: return emptyMap()
        return buildMap {
            put("power", reading(measurement.instantaneousPowerWatts.toDouble()))
            measurement.pedalPowerBalancePercent?.let { put("power_balance", reading(it)) }
            cadenceFrom(
                measurement.cumulativeCrankRevolutions,
                measurement.lastCrankEventTime,
                baseline,
            )?.let { put("cadence", reading(it)) }
        }
    }

    private fun speedAndCadence(
        data: ByteArray,
        baseline: CrankBaseline,
        reading: (Double) -> SensorReading,
    ): Map<String, SensorReading> {
        val measurement = CscMeasurement.parse(data) ?: return emptyMap()
        return buildMap {
            cadenceFrom(
                measurement.cumulativeCrankRevolutions,
                measurement.lastCrankEventTime,
                baseline,
            )?.let { put("cadence", reading(it)) }
            // A wheel-only speed sensor is the common case, and parsing its
            // revolutions only to drop them left it pairing successfully and
            // then reporting nothing at all.
            wheelSpeedFrom(
                measurement.cumulativeWheelRevolutions,
                measurement.lastWheelEventTime,
                baseline,
            )?.let { put("speed_current", reading(it)) }
        }
    }

    private fun runningSpeed(
        data: ByteArray,
        reading: (Double) -> SensorReading,
    ): Map<String, SensorReading> =
        RscMeasurement
            .parse(data)
            ?.let {
                mapOf(
                    "speed_current" to reading(it.speedMps),
                    "cadence" to reading(it.cadenceSpm.toDouble()),
                )
            }.orEmpty()

    /**
     * Metres per second from the wheel counter and a wheel circumference.
     *
     * The circumference is assumed rather than configured: a CSC sensor does not
     * report it, and 2.096 m is the 700x23c default every head unit ships with.
     * A rider on different tyres reads a few percent off, which is far better
     * than the field being empty.
     */
    private fun wheelSpeedFrom(
        revolutions: Long?,
        eventTime: Int?,
        baseline: CrankBaseline,
    ): Double? =
        rateFrom(revolutions, eventTime, baseline.wheel)
            ?.times(WHEEL_CIRCUMFERENCE_METERS)
            ?.div(SECONDS_PER_MINUTE)

    /** Needs two samples, so the first notification after connecting yields nothing. */
    private fun cadenceFrom(
        revolutions: Int?,
        eventTime: Int?,
        baseline: CrankBaseline,
    ): Double? = rateFrom(revolutions?.toLong(), eventTime, baseline.crank)

    /**
     * Revolutions per minute against the previous sample, which this call then
     * becomes. Shared by the crank and the wheel: the arithmetic is the same,
     * only which counter it tracks differs.
     */
    private fun rateFrom(
        revolutions: Long?,
        eventTime: Int?,
        counter: Counter,
    ): Double? {
        if (revolutions == null || eventTime == null) return null
        val previousRevolutions = counter.revolutions
        val previousTime = counter.eventTime
        counter.revolutions = revolutions
        counter.eventTime = eventTime
        if (previousRevolutions == null || previousTime == null) return null
        return RevolutionRate.rpm(previousRevolutions, previousTime, revolutions, eventTime)
    }

    private companion object {
        /** The 700x23c default every head unit ships with, in metres. */
        const val WHEEL_CIRCUMFERENCE_METERS = 2.096

        const val SECONDS_PER_MINUTE = 60.0

        /** Client Characteristic Configuration, the descriptor that arms notifications. */
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun uuid(assigned: String): UUID = UUID.fromString("0000$assigned-0000-1000-8000-00805f9b34fb")

        fun serviceUuid(profile: SensorProfile): UUID = uuid(requireNotNull(profile.serviceUuid))

        /**
         * Null for a profile this class cannot read. Silently falling back to the
         * heart-rate characteristic would subscribe to the wrong thing and then
         * report nothing, which looks identical to a flat battery.
         */
        fun measurementUuid(profile: SensorProfile): UUID? =
            when (profile) {
                SensorProfile.HEART_RATE -> uuid("2A37")
                SensorProfile.CYCLING_SPEED_CADENCE -> uuid("2A5B")
                SensorProfile.CYCLING_POWER -> uuid("2A63")
                SensorProfile.RUNNING_SPEED_CADENCE -> uuid("2A53")
                else -> null
            }

        fun characteristicFor(
            gatt: BluetoothGatt,
            profile: SensorProfile,
        ): BluetoothGattCharacteristic? =
            measurementUuid(profile)?.let { gatt.getService(serviceUuid(profile))?.getCharacteristic(it) }

        val supportedProfiles =
            setOf(
                SensorProfile.HEART_RATE,
                SensorProfile.CYCLING_SPEED_CADENCE,
                SensorProfile.CYCLING_POWER,
                SensorProfile.RUNNING_SPEED_CADENCE,
            )
    }
}
