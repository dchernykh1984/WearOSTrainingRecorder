package com.dchernykh.trainingrecorder.wear.ble

import android.content.Context
import com.dchernykh.trainingrecorder.core.field.SensorProfile
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** A sensor the rider has paired, as it is remembered between rides. */
data class PairedSensor(
    val address: String,
    val name: String?,
    val profileId: String,
) {
    val profile: SensorProfile? get() = SensorProfile.byId(profileId)
}

/**
 * The sensors the rider has paired, kept across rides.
 *
 * Remembered on the watch rather than configured on the phone, unlike almost
 * everything else: pairing is a thing you do standing next to the sensor, and
 * the watch is what has to be next to it.
 */
class PairedSensorStore(
    context: Context,
    private val file: File = File(context.filesDir, "sensors.json"),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): List<PairedSensor> {
        if (!file.exists()) return emptyList()
        return runCatching {
            (json.parseToJsonElement(file.readText()) as? JsonArray).orEmpty().mapNotNull { node ->
                val entry = node as? JsonObject ?: return@mapNotNull null
                val address = string(entry, "address") ?: return@mapNotNull null
                val profileId = string(entry, "profile") ?: return@mapNotNull null
                PairedSensor(address, string(entry, "name"), profileId)
            }
        }.getOrDefault(emptyList())
    }

    fun write(sensors: List<PairedSensor>) {
        val payload =
            buildJsonArray {
                sensors.forEach { sensor ->
                    add(
                        buildJsonObject {
                            put("address", sensor.address)
                            sensor.name?.let { put("name", it) }
                            put("profile", sensor.profileId)
                        },
                    )
                }
            }
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(payload.toString())
        temporary.renameTo(file)
    }

    /** Replaces by address, so re-pairing a sensor updates it rather than doubling it. */
    fun remember(sensor: PairedSensor) {
        write(read().filterNot { it.address == sensor.address } + sensor)
    }

    fun forget(address: String) {
        write(read().filterNot { it.address == address })
    }

    private fun string(
        node: JsonObject,
        key: String,
    ): String? = (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * Keeps every paired sensor connected and merges what they report.
 *
 * One place rather than each screen managing its own connections: a heart-rate
 * strap and a power meter both contribute to the same snapshot, and two callers
 * each holding half of it is how a field ends up reading from a sensor that
 * disconnected ten minutes ago.
 */
class SensorHub(
    private val context: Context,
    private val store: PairedSensorStore = PairedSensorStore(context),
) {
    private val _readings = MutableStateFlow<Map<String, SensorReading>>(emptyMap())

    /** Everything the external sensors currently report, merged by field. */
    val readings: StateFlow<Map<String, SensorReading>> = _readings.asStateFlow()

    private val _connected = MutableStateFlow<Set<SensorProfile>>(emptySet())
    val connected: StateFlow<Set<SensorProfile>> = _connected.asStateFlow()

    private val jobs = mutableListOf<Job>()

    fun paired(): List<PairedSensor> = store.read()

    /** Connects everything paired. Safe to call again; it starts from scratch. */
    fun start(scope: CoroutineScope) {
        stop()
        store.read().forEach { sensor ->
            val profile = sensor.profile ?: return@forEach
            jobs +=
                scope.launch {
                    SensorConnection(context, sensor.address, profile).events().collect { event ->
                        apply(event)
                    }
                }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _readings.value = emptyMap()
        _connected.value = emptySet()
    }

    private fun apply(event: SensorEvent) {
        if (!event.connected) {
            _connected.update { it - event.profile }
            // The readings are left in place rather than cleared: the snapshot
            // ages them out on its own timer, and wiping them here would blank a
            // field on a momentary dropout that reconnects a second later.
            return
        }
        _connected.update { it + event.profile }
        _readings.update { it + event.readings }
    }
}
