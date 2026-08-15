package com.dchernykh.trainingrecorder.wear.ble

import android.content.Context
import com.dchernykh.trainingrecorder.core.field.SensorProfile
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * A sensor the rider has paired, as it is remembered between rides.
 *
 * Several profiles, because one sensor is routinely several: a chest strap that
 * advertises heart rate and running cadence is one device with two things to
 * say, and remembering only one of them is how a strap ends up paired, connected
 * and silent on the field the rider actually wanted.
 */
data class PairedSensor(
    val address: String,
    val name: String?,
    val profileIds: List<String>,
) {
    val profiles: Set<SensorProfile> get() = profileIds.mapNotNull(SensorProfile::byId).toSet()
}

/**
 * The sensors the rider has paired, kept across rides.
 *
 * Remembered on the watch rather than configured on the phone, unlike almost
 * everything else: pairing is a thing you do standing next to the sensor, and
 * the watch is what has to be next to it.
 */
class PairedSensorStore(
    private val file: File,
) {
    /**
     * The app's own directory. A second constructor rather than a defaulted
     * parameter so the file-only form needs no Context at all, which is what
     * lets the format - including the shape an older build wrote - be tested on
     * a plain JVM.
     */
    constructor(context: Context) : this(File(context.filesDir, "sensors.json"))

    private val json = Json { ignoreUnknownKeys = true }

    fun read(): List<PairedSensor> {
        if (!file.exists()) return emptyList()
        return runCatching {
            (json.parseToJsonElement(file.readText()) as? JsonArray).orEmpty().mapNotNull { node ->
                val entry = node as? JsonObject ?: return@mapNotNull null
                val address = string(entry, "address") ?: return@mapNotNull null
                // "profile" is what a single-profile build wrote. Read as well as
                // "profiles" so a rider who upgrades keeps the sensors they had
                // rather than finding the pairing screen empty.
                val profileIds =
                    (entry["profiles"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                        ?: listOfNotNull(string(entry, "profile"))
                if (profileIds.isEmpty()) return@mapNotNull null
                PairedSensor(address, string(entry, "name"), profileIds)
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
                            put(
                                "profiles",
                                buildJsonArray { sensor.profileIds.forEach { add(JsonPrimitive(it)) } },
                            )
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

    /**
     * Which sensors are linked right now, by address.
     *
     * By address rather than by profile because that is the question the pairing
     * screen asks: a rider looking at a row wants to know whether *that strap* is
     * talking, and a screen that cannot say is one they have to test by starting
     * a ride.
     */
    private val _connectedAddresses = MutableStateFlow<Set<String>>(emptySet())
    val connectedAddresses: StateFlow<Set<String>> = _connectedAddresses.asStateFlow()

    /**
     * What each sensor turned out to be once its services were read, which can
     * differ from what it advertised at pairing time.
     */
    private val _profiles = MutableStateFlow<Map<String, Set<SensorProfile>>>(emptyMap())
    val profiles: StateFlow<Map<String, Set<SensorProfile>>> = _profiles.asStateFlow()

    private val jobs = mutableListOf<Job>()

    fun paired(): List<PairedSensor> = store.read()

    /** Connects everything paired. Safe to call again; it starts from scratch. */
    fun start(scope: CoroutineScope) {
        stop()
        store.read().forEach { sensor ->
            jobs +=
                // On the IO dispatcher: a connection that identifies itself
                // writes the corrected profiles back to disk, and the collector
                // would otherwise be doing that on whichever thread the caller's
                // scope runs on - which for a view model is the one drawing the
                // ride.
                scope.launch(Dispatchers.IO) {
                    // A refused Bluetooth permission, a sensor whose profile this
                    // build cannot read, or a stack that throws mid-connect must
                    // cost the rider that one sensor - not the whole ride. The
                    // permission flow promises a refusal is survivable, and an
                    // uncaught SecurityException here would break that promise at
                    // the worst possible moment.
                    runCatching {
                        SensorConnection(context, sensor.address).events().collect { event ->
                            apply(event)
                        }
                    }
                }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _readings.value = emptyMap()
        _connected.value = emptySet()
        _connectedAddresses.value = emptySet()
    }

    /** Corrects what was stored at pairing time, once the sensor has said so itself. */
    private fun remember(
        address: String,
        profiles: Set<SensorProfile>,
    ) {
        if (profiles.isEmpty()) return
        val known = store.read().firstOrNull { it.address == address } ?: return
        val ids = profiles.map { it.id }
        if (known.profileIds.toSet() == ids.toSet()) return
        store.remember(known.copy(profileIds = ids))
        _profiles.update { it + (address to profiles) }
    }

    private fun apply(event: SensorEvent) {
        if (event.discovery) {
            // What the sensor turned out to be, written down so the pairing
            // screen stops calling a heart-rate strap a running sensor - and
            // stops calling it that again after a restart. Deliberately not
            // counted as connected: a sensor that *can* report heart rate has
            // not reported any yet, and it is a reading, not a capability, that
            // takes a field away from the watch.
            remember(event.address, event.profiles)
            return
        }
        if (!event.connected) {
            _connected.update { it - event.profiles }
            _connectedAddresses.update { it - event.address }
            // The readings are left in place rather than cleared: the snapshot
            // ages them out on its own timer, and wiping them here would blank a
            // field on a momentary dropout that reconnects a second later.
            return
        }
        _connected.update { it + event.profiles }
        _connectedAddresses.update { it + event.address }
        _readings.update { it + event.readings }
    }
}
