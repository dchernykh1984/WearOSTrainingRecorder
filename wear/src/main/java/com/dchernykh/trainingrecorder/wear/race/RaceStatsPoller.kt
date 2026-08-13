package com.dchernykh.trainingrecorder.wear.race

import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.race.RaceStatsParser
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.race.RaceStatsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls the timing server while a race is being recorded.
 *
 * Runs only during a recording, as agreed: once a minute over the network is a
 * real battery cost, and standings are meaningless when nobody is riding. A
 * request that fails is simply not emitted - the previous snapshot stays on
 * screen rather than blanking - and the next tick tries again, which is what
 * "retry when connectivity returns" amounts to on a fixed interval.
 */
class RaceStatsPoller(
    private val fetch: suspend (String) -> String? = { httpGet(it) },
) {
    fun poll(config: RaceStatsConfig): Flow<RaceStatsSnapshot> =
        flow {
            val url = RaceStatsUrl.build(config) ?: return@flow
            while (true) {
                fetch(url)?.let { body ->
                    val snapshot = RaceStatsParser.parse(body)
                    if (!snapshot.isEmpty) emit(snapshot)
                }
                delay(config.refreshSeconds.toLong() * MILLIS_PER_SECOND)
            }
        }.flowOn(Dispatchers.IO)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val HTTP_OK = 200

        /**
         * Plain HttpURLConnection: one unauthenticated GET of a few hundred
         * bytes does not justify pulling an HTTP client into the watch APK.
         */
        fun httpGet(url: String): String? =
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.requestMethod = "GET"
                    if (connection.responseCode != HTTP_OK) return null
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
    }
}
