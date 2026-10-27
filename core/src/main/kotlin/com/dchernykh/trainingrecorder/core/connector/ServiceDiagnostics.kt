package com.dchernykh.trainingrecorder.core.connector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Safe protocol evidence, without echoing response bodies, credentials or HTML challenges. */
object ServiceDiagnostics {
    fun http(
        statusCode: Int,
        body: String,
    ): String {
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val codes =
            buildList {
                root?.let { node ->
                    code(node, "error")?.let(::add)
                    (node["responseStatus"] as? JsonObject)?.let { code(it, "type")?.let(::add) }
                    (node["errors"] as? JsonArray)?.filterIsInstance<JsonObject>()?.forEach { error ->
                        listOf("resource", "field", "code").mapNotNull { code(error, it) }.forEach(::add)
                    }
                }
            }.distinct().take(MAX_CODES)
        return (listOf("HTTP $statusCode") + codes).joinToString("; ")
    }

    /** Exception messages can contain URLs with tokens; the type still distinguishes DNS, TLS and timeout. */
    fun network(error: Throwable): String = error.javaClass.simpleName.ifBlank { "IOException" }

    private fun code(
        node: JsonObject,
        key: String,
    ): String? = (node[key] as? JsonPrimitive)?.content?.takeIf { it != "null" && CODE.matches(it) }

    // Free text can echo a login, ticket or token. Only short machine codes are diagnostics.
    private val CODE = Regex("[A-Za-z_][A-Za-z_.-]{0,63}")
    private const val MAX_CODES = 6
}
