package com.dchernykh.trainingrecorder.wear.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.wear.ble.DiscoveredSensor
import com.dchernykh.trainingrecorder.wear.ble.PairedSensor
import com.dchernykh.trainingrecorder.wear.ble.PairedSensorStore
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
) : AndroidViewModel(application) {
    /** The signature `viewModel()` reflects for. See [RecordingViewModel]. */
    constructor(application: Application) : this(application, PairedSensorStore(application), SensorScanner())

    private val _paired = MutableStateFlow(store.read())
    val paired: StateFlow<List<PairedSensor>> = _paired.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredSensor>>(emptyList())
    val discovered: StateFlow<List<DiscoveredSensor>> = _discovered.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob != null) return
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
        _scanning.value = false
    }

    fun pair(sensor: DiscoveredSensor) {
        store.remember(PairedSensor(sensor.address, sensor.name, sensor.profiles.map { it.id }))
        _paired.value = store.read()
    }

    fun forget(sensor: PairedSensor) {
        store.forget(sensor.address)
        _paired.value = store.read()
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
