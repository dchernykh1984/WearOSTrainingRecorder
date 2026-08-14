package com.dchernykh.trainingrecorder.mobile.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket

/**
 * The listener the Strava redirect comes back to.
 *
 * A plain JVM test with real sockets, because sockets are what broke: the
 * failure the rider saw - a timed-out tab against their own phone - came from
 * how connections were accepted, and a mock of the accepting would have agreed
 * with the broken version.
 */
class LoopbackRedirectTest {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    @Test
    fun theRedirectIsReadAndAnswered() {
        LoopbackRedirect.open()!!.use { redirect ->
            val browser = Thread { get(redirect.port, "/exchange_token?code=abc123&state=xyz") }
            browser.start()
            assertEquals("/exchange_token?code=abc123&state=xyz", redirect.awaitTarget(TIMEOUT_MS))
            browser.join()
        }
    }

    @Test
    fun aSilentConnectionAheadOfItDoesNotHoldItUp() {
        // Chrome opens connections speculatively and says nothing on some of
        // them. Served one at a time, the silent one held the accept queue while
        // the request carrying the code was dropped by the kernel - which is a
        // timeout in the browser, not a refusal, and so looks like a dead phone.
        LoopbackRedirect.open()!!.use { redirect ->
            val silent = Socket(loopback, redirect.port)
            val browser = Thread { get(redirect.port, "/exchange_token?code=abc123") }
            browser.start()
            val started = System.currentTimeMillis()
            assertEquals("/exchange_token?code=abc123", redirect.awaitTarget(TIMEOUT_MS))
            val elapsed = System.currentTimeMillis() - started
            assertTrue("waited ${elapsed}ms behind a silent connection", elapsed < SILENT_SOCKET_TIMEOUT_MS)
            browser.join()
            silent.close()
        }
    }

    @Test
    fun severalAtOnceAllGetAnAnswer() {
        // The browser opens up to six. Every one of them has to be replied to,
        // whichever wins: a browser left holding an unanswered socket shows a
        // hung tab, and a rider cannot tell that from a broken app.
        LoopbackRedirect.open()!!.use { redirect ->
            val replies = java.util.Collections.synchronizedList(mutableListOf<String>())
            val browsers =
                (1..PARALLEL_REQUESTS).map { index ->
                    Thread { replies += get(redirect.port, "/exchange_token?code=code$index") }
                }
            browsers.forEach { it.start() }
            assertTrue(redirect.awaitTarget(TIMEOUT_MS)!!.startsWith("/exchange_token?code=code"))
            browsers.forEach { it.join() }
            assertEquals(PARALLEL_REQUESTS, replies.count { it.startsWith("HTTP/1.1 200") })
        }
    }

    @Test
    fun aRiderWhoNeverComesBackIsReportedRatherThanWaitedOnForever() {
        LoopbackRedirect.open()!!.use { redirect ->
            assertNull(redirect.awaitTarget(SHORT_TIMEOUT_MS))
        }
    }

    @Test
    fun closingReleasesThePort() {
        val redirect = LoopbackRedirect.open()!!
        val port = redirect.port
        redirect.close()
        // Rebinding the same port is the observable proof it was let go: a
        // listener left running would hold an authorization endpoint open long
        // after the sign-in it belonged to.
        java.net.ServerSocket(port, 1, loopback).use { assertEquals(port, it.localPort) }
    }

    /** Sends one request and returns the first line of the answer. */
    private fun get(
        port: Int,
        target: String,
    ): String =
        Socket(loopback, port).use { socket ->
            socket.soTimeout = TIMEOUT_MS.toInt()
            socket.getOutputStream().write("GET $target HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
            socket
                .getInputStream()
                .bufferedReader()
                .readLine()
                .orEmpty()
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 200L

        /** The listener's own patience with a silent socket. */
        const val SILENT_SOCKET_TIMEOUT_MS = 5_000L
        const val PARALLEL_REQUESTS = 6
    }
}
