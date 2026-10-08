package com.dchernykh.trainingrecorder.core.connector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * How the phone hands the watch the rider's own service credentials.
 *
 * Separate from [com.dchernykh.trainingrecorder.core.datalayer.SyncContract] on
 * purpose. Settings are copied around freely - dumped while debugging, logged,
 * sent in a bug report - and a token that rides along in a settings payload is a
 * token that leaks. Keeping them on their own path means the two can be handled
 * with the care each deserves.
 *
 * The app ships no keys of its own: every value here belongs to the rider, for
 * their own account, entered once on the phone.
 */
object CredentialContract {
    /** The Data Layer path the phone publishes on and the watch listens to. */
    const val PATH = "/credentials"

    const val KEY_PAYLOAD = "payload"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * What the watch is allowed to be told.
     *
     * The Garmin login and password are stripped. The watch uploads with a
     * token and has no use for a password, and a password copied onto a second
     * device is a second device it can be taken from - which is the whole
     * reason the sign-in happens on the phone.
     *
     * Here, in one place, because the phone now publishes from two: the rider
     * pressing Connect, and the worker that signs in again on its own when a
     * session dies. Two copies of this rule is how a third secret field gets
     * added to one of them and leaks from the other.
     */
    fun publishable(credentials: Map<String, Map<String, String>>): Map<String, Map<String, String>> =
        credentials.mapValues { (connectorId, fields) ->
            if (connectorId == GarminProtocol.ID) {
                fields - GarminProtocol.LOGIN - GarminProtocol.PASSWORD
            } else {
                fields
            }
        }

    fun encode(credentials: Map<String, Map<String, String>>): String =
        buildJsonObject {
            credentials.forEach { (connectorId, fields) ->
                put(connectorId, buildJsonObject { fields.forEach { (key, value) -> put(key, value) } })
            }
        }.toString()

    /**
     * Null rather than an empty map when the payload cannot be read, so a
     * caller can tell "no credentials" from "something went wrong" - the first
     * is a rider who has not set up a service, the second must not overwrite
     * working credentials with nothing.
     */
    fun decode(payload: String): Map<String, Map<String, String>>? =
        runCatching {
            val root = json.parseToJsonElement(payload) as? JsonObject ?: return null
            root
                .mapNotNull { (connectorId, node) ->
                    val fields = node as? JsonObject ?: return@mapNotNull null
                    connectorId to
                        fields
                            .mapNotNull { (key, value) ->
                                (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
                            }.toMap()
                }.toMap()
        }.getOrNull()

    /**
     * Everything a payload would reveal if it were printed, with the values
     * replaced. Used wherever credentials would otherwise reach a log.
     */
    fun redact(credentials: Map<String, Map<String, String>>): String =
        credentials.entries.joinToString(", ") { (id, fields) ->
            "$id=[${fields.keys.sorted().joinToString(",")}]"
        }
}
