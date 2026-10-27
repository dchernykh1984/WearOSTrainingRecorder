package com.dchernykh.trainingrecorder.wear.race

import com.dchernykh.trainingrecorder.core.connector.ServiceDiagnostics
import com.dchernykh.trainingrecorder.core.connector.ServiceRequestException
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.race.RaceStatsParser
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.race.RaceStatsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
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
    /** Injected for the same reason [fetch] is: a test cannot wait a minute. */
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /**
     * Polls until cancelled, asking for the identifiers again every time round.
     *
     * [config] is read per tick rather than captured once, and that is the
     * whole point of it being a function. The rider changes their bib or
     * competition on the phone *during* a race - a number typed wrong at the
     * start line is exactly when this happens - and a loop holding the URL it
     * built at the start kept fetching another rider's standings for the rest
     * of the ride, with nothing to do about it but restart the recording.
     *
     * Re-reading beats restarting the loop: every keystroke on the phone is
     * published, so a restart per change would fire an immediate request per
     * character typed at somebody else's timing server.
     */
    fun poll(
        config: () -> RaceStatsConfig,
        onError: (String?) -> Unit = {},
    ): Flow<RaceStatsSnapshot> =
        flow {
            while (true) {
                val current = config()
                // Null while the identifiers are incomplete - half a bib typed,
                // or cleared mid-race. The loop idles rather than ending, so
                // finishing the number starts the standings without a restart.
                RaceStatsUrl.build(current)?.let { url ->
                    val body =
                        try {
                            fetch(url).also { onError(if (it == null) "GET /live-stats: NO_RESPONSE" else null) }
                        } catch (error: IOException) {
                            onError("GET /live-stats: ${ServiceDiagnostics.network(error)}")
                            null
                        }
                    body?.let {
                        val snapshot = RaceStatsParser.parse(it)
                        if (snapshot.isEmpty) onError("GET /live-stats: NO_STATS") else emit(snapshot)
                    }
                }
                if (!current.isComplete) onError(null)
                sleep(current.refreshSeconds.toLong() * MILLIS_PER_SECOND)
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
        fun httpGet(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                val status = connection.responseCode
                val stream = if (status == HTTP_OK) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status != HTTP_OK) throw ServiceRequestException(ServiceDiagnostics.http(status, body))
                body
            } finally {
                connection.disconnect()
            }
        }
    }
}
