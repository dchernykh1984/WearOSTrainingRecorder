package com.dchernykh.trainingrecorder.wear.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.wear.ble.DiscoveredSensor
import com.dchernykh.trainingrecorder.wear.ble.PairedSensor
import com.dchernykh.trainingrecorder.wear.ble.PairedSensorStore
import com.dchernykh.trainingrecorder.wear.ble.SensorHub
import com.dchernykh.trainingrecorder.wear.ble.SensorScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the pairing screen.
 *
 * Separate from the recording model because it has a different lifetime: the
 * scan must stop the moment the rider leaves this screen, while a recording
 * outlives every screen there is.
 */
class SensorPairingViewModel(
    application: Application,
    private val store: PairedSensorStore = PairedSensorStore(application),
    private val scanner: SensorScanner = SensorScanner(),
    private val hub: SensorHub = SensorHub(application),
) : AndroidViewModel(application) {
    /** The signature `viewModel()` reflects for. See [RecordingViewModel]. */
    constructor(application: Application) : this(
        application,
        PairedSensorStore(application),
        SensorScanner(),
        SensorHub(application),
    )

    /**
     * Which paired sensors are actually linked, while this screen is open.
     *
     * The hub otherwise runs only during a ride, which is the right default for
     * a battery and the wrong one for a screen whose entire job is to say
     * whether a strap is talking. Tied to the screen the same way the scan is:
     * a rider who leaves takes the connections with them.
     */
    val connected: StateFlow<Set<String>> get() = hub.connectedAddresses

    private val _paired = MutableStateFlow(store.read())
    val paired: StateFlow<List<PairedSensor>> = _paired.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredSensor>>(emptyList())
    val discovered: StateFlow<List<DiscoveredSensor>> = _discovered.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob != null) return
        // Connecting while looking, so the rows can say more than "paired".
        hub.start(viewModelScope)
        // A sensor may turn out to be something other than it advertised, and it
        // says so as soon as its services are read. Re-read then, so the row
        // stops describing a heart-rate strap as a running sensor while the
        // rider is looking straight at it.
        viewModelScope.launch { hub.profiles.collect { _paired.value = store.read() } }
        _scanning.value = true
        scanJob =
            viewModelScope.launch {
                // Bluetooth switched off, a refused scan permission and a stack
                // that reports a scan failure all arrive here as an exception.
                // None of them is a reason to take the app down: the screen just
                // finds nothing, which is what it already knows how to show.
                runCatching {
                    scanner
                        .scan()
                        .collect { found ->
                            // Replaced by address rather than appended: a strap
                            // advertises several times a second, and appending would
                            // grow the list without bound while it sat there.
                            _discovered.update { current ->
                                current.filterNot { it.address == found.address } + found
                            }
                        }
                }
                _scanning.value = false
            }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        hub.stop()
        _scanning.value = false
    }

    fun pair(sensor: DiscoveredSensor) {
        store.remember(PairedSensor(sensor.address, sensor.name, sensor.profiles.map { it.id }))
        _paired.value = store.read()
    }

    fun forget(sensor: PairedSensor) {
        store.forget(sensor.address)
        _paired.value = store.read()
        // Restarted rather than left as it was: the hub holds a GATT link per
        // paired sensor, and one to a sensor the rider has just forgotten is a
        // radio kept awake for a device that is no longer theirs.
        hub.start(viewModelScope)
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
