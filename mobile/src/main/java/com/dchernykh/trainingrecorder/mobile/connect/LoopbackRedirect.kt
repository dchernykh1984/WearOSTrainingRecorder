package com.dchernykh.trainingrecorder.mobile.connect

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The loopback listener an OAuth redirect comes back to, per RFC 8252.
 *
 * Split out of [StravaAuthorization] because it is the part that has to survive
 * a browser, and a browser is not a well-behaved HTTP client here. Chrome opens
 * several connections to a host at once - speculatively, before it has anything
 * to send - and some of them never say a word. The first version accepted one
 * connection at a time with a backlog of one, which failed in the least
 * debuggable way available: the kernel drops the connections that do not fit the
 * accept queue rather than refusing them, so the browser sat retransmitting
 * until it gave up and showed the rider ERR_CONNECTION_TIMED_OUT against their
 * own phone. Every connection is now accepted promptly and read on its own
 * thread, and the queue is deep enough to hold the ones that arrive together.
 *
 * Bound to 127.0.0.1 rather than to a wildcard: this is a port that briefly
 * accepts an authorization code, and no other device on the rider's Wi-Fi has
 * any business reaching it.
 */
class LoopbackRedirect private constructor(
    private val server: ServerSocket,
) : Closeable {
    val port: Int get() = server.localPort

    /**
     * The path and query of the first request that carries one, or null if the
     * rider never came back.
     *
     * Requests are answered as they arrive - including the ones that lose this
     * race - because a browser left holding an unanswered socket shows a hung
     * tab, and the rider cannot tell a hung tab from a broken app.
     */
    fun awaitTarget(timeoutMs: Long): String? {
        require(timeoutMs > 0) { "the wait must be positive" }
        val answers = ArrayBlockingQueue<String>(1)
        val threads = Executors.newCachedThreadPool()
        threads.execute {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return@execute
                // Handed straight on: a connection that was opened and never
                // written to must not hold up the one behind it, which is the
                // one carrying the code.
                runCatching { threads.execute { serve(socket, answers) } }.onFailure { socket.close() }
            }
        }
        return try {
            answers.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            threads.shutdownNow()
        }
    }

    private fun serve(
        socket: Socket,
        answers: ArrayBlockingQueue<String>,
    ) = socket.use {
        // Bounded: a connection that opens and says nothing would otherwise hold
        // a thread until the whole authorization timed out.
        it.soTimeout = READ_TIMEOUT_MS
        val requestLine = runCatching { it.getInputStream().bufferedReader().readLine() }.getOrNull().orEmpty()
        if (requestLine.isBlank()) return@use
        runCatching {
            it.getOutputStream().writer().use { writer ->
                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n")
                writer.write("<html><body><p>$DONE_MESSAGE</p></body></html>")
            }
        }
        // "GET /exchange_token?code=... HTTP/1.1" - the middle token is the path
        // and query the protocol knows how to read. Offered rather than put: the
        // first answer is the one that counts, and a second must not block a
        // thread waiting for room that never comes.
        requestLine.split(' ').getOrNull(1)?.let { target -> answers.offer(target) }
    }

    override fun close() {
        runCatching { server.close() }
    }

    companion object {
        /**
         * Deep enough for a browser's habit of connecting several times at once.
         * When this queue is full the kernel drops the connections rather than
         * refusing them, and a dropped connection is a timeout the rider sees.
         */
        private const val BACKLOG = 50

        /** A connection that has said nothing for this long is not the redirect. */
        private const val READ_TIMEOUT_MS = 10_000

        private const val DONE_MESSAGE = "You can close this tab and return to the app."

        /** Null when no port could be opened at all. */
        fun open(): LoopbackRedirect? =
            runCatching { ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1")) }
                .map { LoopbackRedirect(it) }
                .getOrNull()
    }
}
