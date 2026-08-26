package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.dchernykh.trainingrecorder.core.connector.SyncTrigger
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.mobile.segments.SegmentSynchronizer
import com.dchernykh.trainingrecorder.mobile.segments.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The segments the phone holds, and the button that goes and gets more.
 *
 * Split out of [CompanionViewModel] for the same reason the service connections
 * are: everything else on that screen is edited and published on a keystroke,
 * while this reaches the network on request and comes back seconds later with
 * something to say about it.
 */
class SegmentSettings(
    private val synchronizer: SegmentSynchronizer,
) {
    private val _segments = mutableStateOf<List<Segment>>(emptyList())
    val segments: State<List<Segment>> = _segments

    private val _lastSyncEpochMs = mutableStateOf(0L)
    val lastSyncEpochMs: State<Long> = _lastSyncEpochMs

    private val _syncing = mutableStateOf(false)
    val syncing: State<Boolean> = _syncing

    /** What the last refresh had to say, as a string the screen can show. */
    private val _status = mutableStateOf<Int?>(null)
    val status: State<Int?> = _status

    /** Reads what is already on disk, off the main thread. */
    suspend fun load() {
        val stored = withContext(Dispatchers.IO) { runCatching { synchronizer.segments() }.getOrDefault(emptyList()) }
        _segments.value = stored.sortedBy { it.name }
        _lastSyncEpochMs.value = withContext(Dispatchers.IO) { synchronizer.lastSyncEpochMs() }
    }

    /**
     * Goes to Strava now, because the rider asked.
     *
     * The one trigger that ignores the interval: they pressed the button
     * because they believe something has changed, and telling them to come back
     * in five minutes is worse than spending one request finding out.
     */
    suspend fun syncNow() {
        if (_syncing.value) return
        _syncing.value = true
        _status.value = R.string.segments_syncing
        val outcome = withContext(Dispatchers.IO) { runCatching { synchronizer.sync(SyncTrigger.MANUAL) }.getOrNull() }
        _status.value =
            when (outcome) {
                is SyncOutcome.Updated -> R.string.segments_updated
                is SyncOutcome.RateLimited -> R.string.segments_rate_limited
                SyncOutcome.NotConnected -> R.string.segments_not_connected
                SyncOutcome.TooSoon -> R.string.segments_updated
                else -> R.string.segments_failed
            }
        load()
        _syncing.value = false
    }
}
