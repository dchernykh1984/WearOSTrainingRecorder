package com.dchernykh.trainingrecorder.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.dchernykh.trainingrecorder.core.field.SensorProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** A sensor the watch can see, as the pairing screen lists it. */
data class DiscoveredSensor(
    val address: String,
    val name: String?,
    val profile: SensorProfile,
    val rssi: Int,
)

/**
 * Finds nearby fitness sensors.
 *
 * Filtered by service UUID rather than scanning everything and sorting it out
 * afterwards: an unfiltered scan on a watch finds every phone, earbud and TV in
 * the room, and the rider then has to pick their strap out of forty rows. The
 * filter is also what lets the scan say which profile a sensor speaks without
 * connecting to it first.
 */
class SensorScanner(
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
) {
    /**
     * Emits each sensor as it is seen, newest reading of it last.
     *
     * The scan stops when the collector goes away. That matters more here than
     * usual: a BLE scan left running is one of the few things on a watch that
     * will flatten a battery inside an hour.
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredSensor> =
        callbackFlow {
            val scanner =
                adapter?.bluetoothLeScanner
                    ?: run {
                        close(IllegalStateException("bluetooth is off or unavailable"))
                        return@callbackFlow
                    }
            val callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        profileOf(result)?.let {
                            trySend(
                                DiscoveredSensor(
                                    address = result.device.address,
                                    name = result.scanRecord?.deviceName ?: result.device.name,
                                    profile = it,
                                    rssi = result.rssi,
                                ),
                            )
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        close(IllegalStateException("the scan failed with code $errorCode"))
                    }
                }
            // Low latency rather than balanced: this runs only while the rider is
            // looking at a pairing screen and waiting, and a strap that takes
            // fifteen seconds to appear reads as an app that cannot find it.
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(filters, settings, callback)
            awaitClose { scanner.stopScan(callback) }
        }

    /**
     * Which profile the advertisement claims. A sensor advertising several - a
     * power meter that also speaks cadence - resolves to the richest one, so
     * pairing it once gets everything it can report.
     */
    private fun profileOf(result: ScanResult): SensorProfile? {
        val advertised =
            result.scanRecord
                ?.serviceUuids
                .orEmpty()
                .map { it.uuid }
                .toSet()
        return scannableProfiles.firstOrNull { serviceUuidOf(it) in advertised }
    }

    private companion object {
        /**
         * Ordered richest first, which is what makes a combined sensor resolve to
         * the profile that carries the most: a power meter read as plain cadence
         * would silently throw away the watts.
         */
        val scannableProfiles =
            listOf(
                SensorProfile.CYCLING_POWER,
                SensorProfile.CYCLING_SPEED_CADENCE,
                SensorProfile.RUNNING_SPEED_CADENCE,
                SensorProfile.HEART_RATE,
            )

        fun serviceUuidOf(profile: SensorProfile): UUID =
            UUID.fromString("0000${requireNotNull(profile.serviceUuid)}-0000-1000-8000-00805f9b34fb")

        val filters: List<ScanFilter> =
            scannableProfiles.map {
                ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuidOf(it))).build()
            }
    }
}
