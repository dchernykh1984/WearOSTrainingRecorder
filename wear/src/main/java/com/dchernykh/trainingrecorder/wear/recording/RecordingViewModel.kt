package com.dchernykh.trainingrecorder.wear.recording

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.field.FieldValues
import com.dchernykh.trainingrecorder.core.field.Surroundings
import com.dchernykh.trainingrecorder.core.fit.RecordedWorkout
import com.dchernykh.trainingrecorder.core.fit.TrackPoint
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.race.RaceStatsSnapshot
import com.dchernykh.trainingrecorder.core.recording.RecordingAction
import com.dchernykh.trainingrecorder.core.recording.RecordingPhase
import com.dchernykh.trainingrecorder.core.recording.RecordingState
import com.dchernykh.trainingrecorder.core.segment.Segment
import com.dchernykh.trainingrecorder.core.segment.SegmentTracker
import com.dchernykh.trainingrecorder.core.sensor.FixStatus
import com.dchernykh.trainingrecorder.core.sensor.SensorOrigin
import com.dchernykh.trainingrecorder.core.sensor.SensorReading
import com.dchernykh.trainingrecorder.core.sensor.SensorSnapshot
import com.dchernykh.trainingrecorder.core.solar.SolarEvents
import com.dchernykh.trainingrecorder.core.solar.SolarTimes
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.core.track.AltitudeTracker
import com.dchernykh.trainingrecorder.core.track.CumulativeBaseline
import com.dchernykh.trainingrecorder.core.track.Fix
import com.dchernykh.trainingrecorder.core.track.Gradient
import com.dchernykh.trainingrecorder.core.track.RideAggregates
import com.dchernykh.trainingrecorder.core.track.RideTrack
import com.dchernykh.trainingrecorder.core.track.RollingPower
import com.dchernykh.trainingrecorder.core.workout.SportOrdering
import com.dchernykh.trainingrecorder.wear.ble.SensorHub
import com.dchernykh.trainingrecorder.wear.health.BuiltInSample
import com.dchernykh.trainingrecorder.wear.health.ExerciseRecorder
import com.dchernykh.trainingrecorder.wear.race.RaceStatsPoller
import com.dchernykh.trainingrecorder.wear.segment.SegmentStore
import com.dchernykh.trainingrecorder.wear.service.RecordingService
import com.dchernykh.trainingrecorder.wear.storage.TrackJournalStore
import com.dchernykh.trainingrecorder.wear.storage.WorkoutRepository
import com.dchernykh.trainingrecorder.wear.sync.SettingsStore
import com.dchernykh.trainingrecorder.wear.sync.WorkoutPublisher
import com.dchernykh.trainingrecorder.wear.upload.CredentialStore
import com.dchernykh.trainingrecorder.wear.upload.UploadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Ties the recording together: the state machine, Health Services, the samples
 * collected for the FIT file, and the values the screens read.
 *
 * The screens read [values], a snapshot map recomputed on a ticker rather than
 * pulled on demand. Composables cannot observe a function call, so a display
 * that asked the model for each field would render once and then freeze - which
 * on a workout screen looks exactly like a stopped watch.
 *
 * Past the function threshold on purpose: this is the one place that owns the
 * whole recording - the state machine, Health Services, the sensors, the race
 * poll and the save. Splitting it would only move the coordination elsewhere and
 * add a seam where those lifecycles have to be kept in step by hand.
 */
@Suppress("TooManyFunctions")
class RecordingViewModel(
    application: Application,
    private val recorder: ExerciseRecorder = ExerciseRecorder(application),
    private val repository: WorkoutRepository = WorkoutRepository(application),
    private val settings: SettingsStore = SettingsStore(application),
    private val poller: RaceStatsPoller = RaceStatsPoller(),
    private val hub: SensorHub = SensorHub(application),
) : AndroidViewModel(application) {
    /**
     * Kotlin default arguments do not emit a one-argument constructor, and
     * `viewModel()` reflects for exactly that signature - without this the app
     * dies on its first frame with "cannot create an instance".
     */
    constructor(application: Application) : this(
        application,
        ExerciseRecorder(application),
        WorkoutRepository(application),
        SettingsStore(application),
        RaceStatsPoller(),
        SensorHub(application),
    )

    /**
     * Owned outright rather than injected like the collaborators above. Nothing
     * outside this model ever writes the journal - it is opened, appended to and
     * dropped entirely within one recording - and the constructor is already as
     * long as it should get.
     */
    private val journal = TrackJournalStore(application)

    /**
     * One thread, so journal work happens in the order it was asked for.
     *
     * The calls are individually safe - the store is synchronized - but their
     * order is the whole meaning: a discard that overtakes the begin it was
     * meant to undo leaves an orphan journal, and samples handed to a begin that
     * has not run yet are written nowhere. Dispatchers.IO is a pool and gives no
     * such guarantee; one thread does, and one thread is what a file appended to
     * once a second needs.
     */
    private val journalExecutor = Executors.newSingleThreadExecutor()
    private val journalContext = journalExecutor.asCoroutineDispatcher()

    /** Same reasoning: nothing outside this model publishes a saved ride. */
    private val publisher = WorkoutPublisher(application)

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())

    /** What each field currently reads, recomputed once a second. */
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val sensors = MutableStateFlow(SensorSnapshot())
    private val raceStats = MutableStateFlow(RaceStatsSnapshot.EMPTY)
    private val history = MutableStateFlow(settings.readHistory())

    /**
     * The sport picker's order: recency by kind.
     *
     * Observable rather than a plain getter, so the picker actually reorders
     * after a workout is saved instead of only on the next launch.
     */
    val sports: StateFlow<List<SportType>> =
        history
            .map { SportOrdering.order(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SportOrdering.order(history.value))

    /**
     * The sports worth a single tap, which is what the picker leads with. The
     * rest of the catalogue is browsed by discipline and needs no state here.
     */
    val favourites: StateFlow<List<SportType>> =
        history
            .map { SportOrdering.favourites(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SportOrdering.favourites(history.value))

    /**
     * Where the position has got to, for the indicator on the ride screen.
     *
     * Straight from the recorder: the fix is the platform's answer, and there is
     * nothing for this class to add to it.
     */
    val fix: Flow<FixStatus> get() = recorder.fix

    /** Whether this sport has a position to track, and so an indicator to show. */
    fun tracksPosition(sport: SportType): Boolean = recorder.needsGps(sport.id)

    /** Drops a sport from the shortcut list. It stays in the catalogue. */
    fun forgetFavourite(sport: SportType) {
        history.value = SportOrdering.forget(history.value, sport.id).also(settings::writeHistory)
    }

    private val _configuration = MutableStateFlow(ScreenConfiguration.initial())

    /**
     * The screen layouts, observed rather than read once.
     *
     * A plain field meant the pager drew whatever the configuration was when the
     * ride started, so a rider rearranging their fields on the phone had to
     * finish and start again to see it. Nothing about the recording depends on
     * this - the samples come from Health Services and the sensors whatever is
     * on screen - so it is only ever a question of what is drawn.
     */
    val configuration: StateFlow<ScreenConfiguration> = _configuration.asStateFlow()
    private var units: UnitSystem = UnitSystem.METRIC
    private var race: RaceStatsConfig = RaceStatsConfig()

    private val samples = mutableListOf<TrackPoint>()

    /**
     * What the ride works out for itself from its own positions.
     *
     * Not a second opinion: for distance, speed and climb these *are* the
     * answer whenever there are fixes to compute them from. Health Services
     * reported zero metres across an hour of real cycling with a green
     * satellite indicator beside it, and a confident zero is indistinguishable
     * from a rider who did not move - so the app stopped asking it.
     */
    private val track = RideTrack()

    /**
     * The last position the platform reported, held between its batches.
     *
     * The track is written every second and fixes do not arrive every second, so
     * a point takes the most recent one. Repeating a position the rider has not
     * moved from is what a head unit does too; inventing one they might be at is
     * not.
     */
    private var lastFix: Fix? = null

    /** When the last point was written, so a second cannot be recorded twice. */
    private var lastPointAtEpochMs = 0L

    /**
     * The rider's starred segments, and where the ride stands against them.
     *
     * Loaded when the ride starts and not touched again. Everything it needs was
     * fetched on the phone beforehand, which is what lets it work on a climb
     * with no signal - and rebuilding it mid-ride to pick up a segment starred
     * five minutes ago would throw away the effort the rider is in the middle of.
     */
    private var segments = SegmentTracker()

    /**
     * Where the starred segments live. Owned rather than injected for the same
     * reason the journal is: nothing outside this model reads them during a
     * ride, and the constructor is already long.
     */
    private val segmentStore = SegmentStore(application)

    /**
     * The segments as last read from disk, kept in memory.
     *
     * Read here rather than when the ride starts, because starting a ride is
     * the one moment in the app that must not stall: a rider with ninety
     * starred climbs would otherwise have a megabyte of JSON parsed under the
     * tap that begins their ride. Volatile because the read happens on the IO
     * dispatcher and the tap that uses it does not.
     */
    @Volatile
    private var storedSegments: List<Segment> = emptyList()
    private val altitude = AltitudeTracker()
    private val aggregates = RideAggregates()
    private val rollingPower = RollingPower()

    /**
     * The platform's running totals, counted from this ride rather than from
     * whenever its own session began.
     */
    private val platformTotals = CumulativeBaseline()

    /** What the ride has reported so far, which cannot go down. */
    private var reportedDistanceMeters = 0.0
    private val gradient = Gradient()

    /**
     * When the sun rises and sets where the ride is, and when that was worked
     * out.
     *
     * Recomputed sparingly on purpose. The answer is a property of a place and a
     * date, and neither moves fast: a rider crossing half a time zone changes
     * their sunset by a couple of minutes, and a rider on an aeroplane is not
     * reading this field. Once when a position first arrives, and every half
     * hour after that, is far more often than the sky requires and rare enough
     * to cost nothing.
     */
    private var solar: SolarEvents? = null
    private var solarAtEpochMs = 0L

    /**
     * The last reading seen for each built-in metric, rather than only what the
     * newest update carried. Health Services batches by data type, so any single
     * update is a partial picture.
     */
    private val builtIn = mutableMapOf<String, SensorReading>()
    private var sampleJob: Job? = null
    private var raceJob: Job? = null
    private var tickJob: Job? = null

    /** Guards the transitions: each one awaits Health Services first. */
    private var busy = false

    init {
        applySettings()
        // Re-read whenever the phone pushes, including mid-ride. The listener
        // that receives the push runs in this process, so this is the whole
        // mechanism.
        // Off the main thread: applying settings reads a file, and this now runs
        // on every push from the phone rather than once at startup.
        viewModelScope.launch(Dispatchers.IO) { SettingsStore.revision.collect { applySettings() } }
        readSegments()
        // On the first construction after a launch, which is exactly when a ride
        // the last process never got to finish is sitting on disk waiting to be
        // noticed.
        viewModelScope.launch(journalContext) { recoverInterruptedRide() }
    }

    /**
     * Saves the ride a previous run was killed in the middle of.
     *
     * Recovered rather than offered: a dialog asking whether to keep a ride the
     * rider has already ridden is a question with one sensible answer, and the
     * one moment it would be asked - the next time they open the app, probably to
     * start something else - is the worst moment to ask it.
     *
     * The workout id is derived from the start time, the same way a normal save
     * derives it, so a ride that was in fact written out before the process died
     * lands on itself rather than beside itself.
     */
    private fun recoverInterruptedRide() {
        val ride = journal.recover() ?: return
        val workout = runCatching { ride.toWorkout() }.getOrNull()
        if (workout == null) {
            // A journal that cannot become a workout will not become one on the
            // next launch either, and keeping it would mean trying forever.
            Log.w(TAG, "an interrupted ride could not be rebuilt from its journal")
            journal.discardRecovered()
            return
        }
        val saved = runCatching { repository.save("workout-${ride.startedAtEpochMs}", workout, CONNECTORS) }
        // Kept when the save failed: the journal is still the only copy, and the
        // next launch gets another chance at it.
        if (saved.isSuccess) {
            // The claimed copy, not the live journal: the rider may well have
            // started a new ride while this was encoding, and that one's journal
            // is not this one's to close.
            journal.discardRecovered()
            history.value = SportOrdering.record(history.value, ride.sportTypeId).also(settings::writeHistory)
            publisher.publish(repository)
            UploadWorker.schedule(getApplication())
        }
    }

    /**
     * Pulls whatever the phone has already published.
     *
     * The listener only hears *changes*, so a watch installed after the phone
     * was configured would otherwise sit on defaults - in metric, with no race
     * and no credentials - until the rider went and touched a setting.
     */
    fun syncFromPhone() {
        viewModelScope.launch {
            SettingsStore.fetchExisting(getApplication())?.let { settings.write(it) }
            CredentialStore.fetchExisting(getApplication())?.let {
                CredentialStore(getApplication()).write(it)
            }
            // Segments too, and for the same reason: a watch installed after the
            // phone had already fetched them would otherwise have none until the
            // rider starred something new.
            SegmentStore.fetchExisting(getApplication()).forEach { segmentStore.write(it) }
            readSegments()
            applySettings()
        }
    }

    /** Reads the starred segments off the main thread. */
    private fun readSegments() {
        viewModelScope.launch(Dispatchers.IO) {
            storedSegments = runCatching { segmentStore.read() }.getOrDefault(emptyList())
        }
    }

    /** Re-read whenever the screen comes back, so a phone push lands promptly. */
    fun applySettings() {
        settings.read()?.let {
            _configuration.value = it.screens
            units = it.units
            race = it.race
        }
    }

    fun screensFor(sport: SportType) = _configuration.value.resolve(sport)

    fun start(sport: SportType) {
        // PREPARING counts as started even though the state machine does not call
        // it active: between the tap and Health Services answering there is a
        // window where a second tap would start a second session.
        if (busy || _state.value.phase.isActive || _state.value.phase == RecordingPhase.PREPARING) return
        applySettings()
        // Cleared with the samples: these are cumulative totals, and a second
        // ride that started by showing the first one's distance would also save
        // it if Health Services had not sent a fresh batch by the finish.
        builtIn.clear()
        samples.clear()
        // Everything the ride works out for itself goes with the ride.
        track.clear()
        lastFix = null
        lastPointAtEpochMs = 0
        altitude.clear()

        aggregates.clear()
        rollingPower.clear()
        platformTotals.clear()
        reportedDistanceMeters = 0.0
        gradient.clear()
        // Read from disk here rather than held: a segment that arrived from the
        // phone while the watch sat at the trailhead should be raced today.
        segments = SegmentTracker(storedSegments)
        // And go back to disk for the next ride, so a segment that arrived from
        // the phone while the watch sat at the trailhead is raced tomorrow even
        // if the app is never reopened.
        readSegments()
        // Cleared with the ride: the sun's timetable belongs to where and when
        // that ride was, and a ride started somewhere else tomorrow must not
        // inherit it.
        solar = null
        solarAtEpochMs = 0
        sensors.value = SensorSnapshot()
        val startedAt = now()
        _state.update { it.prepare(sport.id, startedAt) }
        // Opened with the same start time the save will use for the id, so a ride
        // recovered from this journal is the same workout the finish would have
        // written rather than a second copy of it.
        viewModelScope.launch(journalContext) { journal.begin(sport.id, startedAt) }
        RecordingService.start(getApplication())
        // Started with the workout, not held open between rides: a GATT link to
        // a power meter kept alive all day is a flat battery on both ends.
        hub.start(viewModelScope)
        // A ticker rather than only recomputing when a sample lands: the timers
        // have to keep counting through a tunnel, where no sample lands at all.
        tickJob?.cancel()
        tickJob =
            viewModelScope.launch {
                while (true) {
                    recomputeValues()
                    delay(TICK_MS)
                }
            }
        sampleJob =
            viewModelScope.launch {
                try {
                    recorder.start(sport.id)
                    _state.update { current ->
                        if (current.phase == RecordingPhase.PREPARING) current.begin(now()) else current
                    }
                    startRacePolling()
                    // Inside the try, not after it: Health Services reports a
                    // failed callback registration by closing the flow, and that
                    // would otherwise reach the scope uncaught and kill the app
                    // mid-ride rather than end the recording.
                    collectSamples()
                } catch (cancellation: CancellationException) {
                    // The scope is going away, so the exercise has to be ended
                    // explicitly - discard() would launch that end on the very
                    // scope being cancelled, and the platform would keep GNSS
                    // running for a session with no app attached.
                    endOrphanedExercise()
                    throw cancellation
                } catch (
                    @Suppress("TooGenericExceptionCaught") failure: Exception,
                ) {
                    Log.w(TAG, "the recording could not be started", failure)
                    discard()
                }
            }
    }

    private fun startRacePolling() {
        if (!race.isComplete) return
        raceJob?.cancel()
        raceJob = viewModelScope.launch { poller.poll(race).collect { raceStats.value = it } }
    }

    private suspend fun collectSamples() {
        recorder.samples().collect { sample ->
            val timestamp = now()
            // Folded into what came before rather than replacing it. Health
            // Services sends whatever that batch happened to carry, so an update
            // without the distance aggregate would blank the field - and, at the
            // end of a ride, save a workout that says it covered nothing.
            // The platform's own elevation is deliberately not folded in. It goes
            // to the altitude tracker as an input and comes back out through
            // `derived`, which is the only thing that knows whether the source
            // has settled - folded in here as well it would reach the recorded
            // track behind that judgement's back.
            builtIn += fromStartOfRide(sample.readings.filterKeys { it != "altitude" })
            builtIn += derived(sample, timestamp)
            sensors.value =
                SensorSnapshot.merge(
                    external = hub.readings.value,
                    builtIn = builtIn.toMap(),
                    nowEpochMs = timestamp,
                    connectedProfiles = hub.connected.value,
                )
            // Where the ride is, kept for the recorder to stamp on the points it
            // writes between batches. A fix stays true until the next one
            // arrives - a rider does not stop existing because the platform is
            // saving power.
            sample.fixes.lastOrNull()?.let { lastFix = it }
            // One notion of where the ride is, and it is this one. The sample
            // used to carry a second position of its own, stamped on arrival,
            // which is what read as nine hundred kilometres an hour - it is gone
            // rather than left about to be picked up again.
            lastFix?.let { refreshSolar(it.latitudeDeg, it.longitudeDeg, timestamp) }
            recomputeValues()
        }
    }

    fun onAction(action: RecordingAction) {
        // Every transition awaits Health Services, and a second tap during that
        // window would hit a state the machine refuses - which is a crash, not a
        // no-op. Ignoring the tap is the honest response.
        if (busy) return
        when (action) {
            RecordingAction.PAUSE -> transition({ recorder.pause() }) { it.pause(now()) }
            RecordingAction.RESUME -> transition({ recorder.resume() }) { it.resume(now()) }
            RecordingAction.FINISH -> {
                busy = true
                viewModelScope.launch { finish() }
            }
            RecordingAction.DISCARD -> discard()
            RecordingAction.START -> Unit
        }
    }

    private fun transition(
        call: suspend () -> Unit,
        apply: (RecordingState) -> RecordingState,
    ) {
        busy = true
        val before = _state.value.phase
        viewModelScope.launch {
            try {
                // The transition is only applied if the platform accepted it, so
                // a refused pause leaves the screens saying RECORDING - which is
                // what the watch is actually doing.
                if (!runCatching { call() }.isSuccess) return@launch
                // And only if the recording is still where it was. A discard
                // while the platform was answering leaves a state the transition
                // refuses, and refusal here means a crash rather than a no-op.
                _state.update { if (it.phase == before) apply(it) else it }
            } finally {
                busy = false
            }
        }
    }

    private suspend fun finish() {
        try {
            // Captured before the platform is asked: a discard racing this would
            // otherwise leave finish() applying a transition the state machine
            // refuses outright.
            if (!_state.value.phase.isActive) return
            // Flushed before ending: Health Services batches samples, and without
            // this the last minutes of the ride are still in the buffer.
            runCatching { recorder.flush() }
            runCatching { recorder.end() }
            stopCollecting()
            if (!_state.value.phase.isActive) return
            _state.update { it.finish(now()) }
            save(_state.value)
            RecordingService.stop(getApplication())
        } finally {
            busy = false
        }
    }

    private suspend fun save(finished: RecordingState) {
        val sportId = finished.sportTypeId ?: return
        val start = finished.startedAtEpochMs ?: return
        // Encoding a six-hour ride is thousands of messages plus a file write and
        // an index rewrite; on the main thread that is an ANR at exactly the
        // moment the rider is waiting to see their ride saved.
        // One uncancellable block, not several. A rider who long-presses Finish
        // and immediately swipes the app away cancels this scope, and every
        // return from a withContext resumes on it: split into two, the first
        // block would run and the resumption between them would throw, leaving
        // the ride on disk but never queued, never published, and its journal
        // still in the way of the next one.
        withContext(Dispatchers.IO + NonCancellable) {
            val saved = runCatching { repository.save("workout-$start", recorded(finished, start), CONNECTORS) }
            // The samples are the only copy of the ride until the file exists,
            // so they are kept if the write failed: the next FINISH still has a
            // ride to write, rather than saving an empty one on top of a lost
            // one.
            if (saved.isSuccess) {
                samples.clear()
                // Dropped only now, once the ride exists as a file. Deleting it
                // any earlier would leave a window where a kill loses the ride
                // from both places at once.
                withContext(journalContext) { journal.finish(start) }
                // Queued before the phone is told, because this is the part that
                // matters: the workout should be on its way before the rider has
                // put the watch down.
                UploadWorker.schedule(getApplication())
                // The phone's history is fed from here: it holds no workouts of
                // its own, so a ride it is never told about is one it can never
                // show.
                publisher.publish(repository)
            }
            history.value = SportOrdering.record(history.value, sportId).also(settings::writeHistory)
        }
    }

    /**
     * The workout as the encoder wants it.
     *
     * Built inside the save's runCatching rather than before it: a clock adjusted
     * backwards across a pause can leave elapsed shorter than moving, which the
     * constructor refuses - and an exception here would be a crash at the one
     * moment the ride cannot be lost.
     */
    private fun recorded(
        finished: RecordingState,
        start: Long,
    ): RecordedWorkout {
        val now = now()
        return RecordedWorkout(
            sportTypeId = finished.sportTypeId.orEmpty(),
            startedAtEpochMs = start,
            totalTimerSeconds = finished.movingMillisAt(now) / MILLIS_PER_SECOND,
            totalElapsedSeconds = finished.elapsedMillisAt(now) / MILLIS_PER_SECOND,
            totalDistanceMeters = sensors.value.value("distance_total") ?: 0.0,
            // The watch's own totals rather than whatever a service derives from
            // the altitude series: this is the only place that knows which part
            // of a change in height was a hill and which was the datum arriving.
            totalAscentMeters = altitude.ascentMeters,
            totalDescentMeters = altitude.descentMeters,
            points = samples.toList(),
        )
    }

    /**
     * Ends a session the app can no longer own.
     *
     * On its own scope, because the reason it is called is that the view model's
     * scope has been cancelled - launching the end there would simply not run,
     * and the watch would keep a GNSS session alive for nobody.
     */
    private fun endOrphanedExercise() {
        CoroutineScope(Dispatchers.IO).launch { runCatching { recorder.end() } }
    }

    fun discard() {
        val discarded = _state.value.startedAtEpochMs
        viewModelScope.launch { runCatching { recorder.end() } }
        stopCollecting()
        _state.value = RecordingState()
        samples.clear()
        // A discarded ride is thrown away deliberately, so its journal goes with
        // it - left behind, the next launch would helpfully recover the very
        // thing the rider just decided not to keep. Named, so a discard that
        // lands late cannot take a newer ride's journal with it.
        discarded?.let {
            // On the journal thread like everything else, so a discard cannot
            // overtake the begin it is undoing. Detached from the view model's
            // scope, because discarding is often the last thing that happens
            // before the screen goes away and takes that scope with it.
            CoroutineScope(journalContext).launch { journal.finish(it) }
        }
        busy = false
        RecordingService.stop(getApplication())
    }

    private fun stopCollecting() {
        hub.stop()
        // Cancelled explicitly so the flow's awaitClose runs and Health Services
        // drops the callback; left running, every new workout stacks another
        // collector on top of the last.
        sampleJob?.cancel()
        sampleJob = null
        raceJob?.cancel()
        raceJob = null
        tickJob?.cancel()
        tickJob = null
        recomputeValues()
    }

    override fun onCleared() {
        stopCollecting()
        // Shut down after the queue drains, so a finish queued on the way out
        // still runs; left open it would be a thread per view model.
        journalExecutor.shutdown()
        super.onCleared()
    }

    /**
     * The track point for this second.
     *
     * Written on the clock rather than when Health Services delivers, which is
     * the bug this replaced. That platform batches - with the screen off, a
     * minute or more at a time - so an hour of riding was recorded as a few
     * dozen points, and each was stamped with the moment its batch happened to
     * be processed rather than the moment it described. The values were right,
     * which is why nothing on the watch looked wrong: it was the clock that was
     * missing. Downstream the damage was total, because a services reading the
     * file sees only the points - an hour's ride arrived at Strava as fifty-two
     * seconds, and its average heart rate was computed over a handful of
     * clustered samples.
     *
     * A second is what every head unit records at, and it is the same tick the
     * screen is drawn from, so what is written is by construction what the rider
     * was shown.
     */
    private fun recordTrackPoint(nowEpochMs: Long) {
        // A paused ride must not lay down a straight line between where the
        // rider stopped and where they started again.
        if (_state.value.phase != RecordingPhase.RECORDING) return
        // One point per second, whoever asked. The values are recomputed both on
        // the tick and whenever a batch lands, and two points sharing a second
        // is a duplicate sample in the file for no gain.
        //
        // Only a gap that is short *and forwards* skips. A clock that steps
        // backwards - which any watch does when it syncs - would otherwise leave
        // this test true for as long as the step was, and the ride would simply
        // stop being recorded until the clock caught up.
        val sinceLast = nowEpochMs - lastPointAtEpochMs
        if (sinceLast in 0 until TICK_MS) return
        lastPointAtEpochMs = nowEpochMs
        val point =
            TrackPoint(
                timestampEpochMs = nowEpochMs,
                latitudeDeg = lastFix?.latitudeDeg,
                longitudeDeg = lastFix?.longitudeDeg,
                altitudeMeters = sensors.value.value("altitude"),
                heartRateBpm = sensors.value.value("hr")?.toInt(),
                cadenceRpm = sensors.value.value("cadence")?.toInt(),
                speedMps = sensors.value.value("speed_current"),
                powerWatts = sensors.value.value("power")?.toInt(),
                distanceMeters = sensors.value.value("distance_total"),
            )
        samples += point
        // Queued rather than awaited: the journal thread keeps them in order
        // behind the begin that opened the file, and the ticker has no reason to
        // wait for a write it does not read back.
        val moving = _state.value.movingMillisAt(nowEpochMs)
        viewModelScope.launch(journalContext) { journal.append(point, moving) }
    }

    private fun recomputeValues() {
        val now = now()
        // Re-merged on the tick, not only when Health Services delivers a batch:
        // a strap notifying every second would otherwise sit unread through a
        // tunnel, and a disconnected one would never age out of the display.
        sensors.value =
            SensorSnapshot.merge(
                external = hub.readings.value,
                builtIn = builtIn.toMap(),
                nowEpochMs = now,
                connectedProfiles = hub.connected.value,
            )
        _values.value =
            FieldValues.snapshot(
                state = _state.value,
                nowEpochMs = now,
                sensors = sensors.value,
                race = raceStats.value,
                units = units,
                surroundings =
                    Surroundings(
                        // The watch's own zone, so the clock field is not UTC.
                        clockOffsetMinutes = TimeZone.getDefault().getOffset(now) / MILLIS_PER_MINUTE,
                        solar = solar,
                    ),
                segment = segments.state,
            )
        // After the merge, so the point carries exactly the values the screen is
        // about to show.
        recordTrackPoint(now)
    }

    /**
     * What the ride says about height.
     *
     * The altitude only once the source has settled: written while the altimeter
     * is still converging it goes into the recorded track as a ramp, and a
     * service computing its own ascent from that ramp hands back the very climb
     * we refused to count.
     *
     * The totals from the first reading onwards, zero included. Left to the
     * platform's own figure until ours was non-zero, the field showed something
     * gathered on other terms - and a ride that has climbed nothing has climbed
     * nothing, which is worth showing rather than a gap.
     */
    private fun heightValues(): Map<String, Double> =
        buildMap {
            if (altitude.trustworthy) altitude.altitudeMeters?.let { put("altitude", it) }
            if (altitude.measuring) {
                put("ascent_total", altitude.ascentMeters)
                put("descent_total", altitude.descentMeters)
            }
        }

    /**
     * The platform's readings, with its running totals rebased on this ride.
     *
     * An exercise session that was already under way hands over its accumulated
     * figures on the first update, and the ride would otherwise open having
     * covered nine hundred metres and climbed to somebody else's altitude -
     * which reaches the file as a first point no service can make sense of.
     */
    private fun fromStartOfRide(readings: Map<String, SensorReading>): Map<String, SensorReading> =
        readings.mapValues { (field, reading) ->
            if (field in PLATFORM_TOTALS) {
                reading.copy(value = platformTotals.sinceStart(field, reading.value))
            } else {
                reading
            }
        }

    /**
     * What the ride knows that Health Services does not: distance and speed from
     * the positions, altitude from the barometer against the sky, climb from
     * that altitude, and the averages and maxima nothing else ever computed.
     *
     * Folded in after the platform's own readings and therefore on top of them,
     * because where both have an answer this one is the answer the saved track
     * agrees with.
     */
    private fun derived(
        sample: BuiltInSample,
        nowEpochMs: Long,
    ): Map<String, SensorReading> {
        // Every position in the batch, each at the time it was measured. Stamped
        // on arrival instead, a batch released when the wrist turns puts a
        // minute of travel into a few milliseconds and reads as hundreds of
        // kilometres an hour.
        sample.fixes.forEach(track::record)
        // The same fixes, against the segments the phone sent. Nothing is
        // fetched here and nothing can be: this runs on a climb.
        sample.fixes.forEach(segments::record)
        // The barometer arrives as an ordinary reading; the fix carries its own.
        // Which reading is shown, which one the climb is measured from, and why
        // they are not the same one, is AltitudeTracker's.
        altitude.record(
            barometricMeters = sample.readings["altitude"]?.value,
            // The fix's own altitude, not the sample's - that one has already
            // fallen back to the barometer, and calibrating a barometer against
            // itself records a perfect agreement that means nothing and then
            // holds it for an hour.
            gnssMeters = sample.gnssAltitudeMeters,
            nowEpochMs = nowEpochMs,
        )
        aggregates.record(sensors.value.readings.mapValues { it.value.value })
        // Power as a rider reads it: instantaneous watts swing a hundred either
        // way between the top and bottom of a pedal stroke, and nobody can hold
        // to a number that moves ten times a second.
        sensors.value.value("power")?.let { rollingPower.record(it, nowEpochMs) }
        // How steep it is and how fast the height is coming, both measured over
        // ground rather than between two samples.
        altitude.altitudeMeters?.let { gradient.record(it, track.distanceMeters, nowEpochMs) }

        val moving = _state.value.movingMillisAt(nowEpochMs) / MILLIS_PER_SECOND
        val values =
            buildMap {
                // Only where there is a track to measure. Indoors there is none,
                // and Health Services' own distance - from the accelerometer on
                // a treadmill - is then the better answer and the one left in
                // place.
                // Never less than it already was. Ground covered cannot be
                // uncovered, and the moment the ride's own measurement takes
                // over from the platform's the two need not agree - a file whose
                // distance steps backwards is one a service has to guess about.
                val platform = builtIn["distance_total"]?.value ?: 0.0
                reportedDistanceMeters = maxOf(reportedDistanceMeters, track.distanceMeters, platform)
                put("distance_total", reportedDistanceMeters)
                track.speedMps?.let { put("speed_current", it) }
                putAll(heightValues())
                gradient.percent()?.let { put("grade", it) }
                gradient.verticalSpeedMetersPerHour()?.let { put("vertical_speed", it) }
                rollingPower.average(RollingPower.THREE_SECONDS_MS, nowEpochMs)?.let { put("power_3s", it) }
                rollingPower.average(RollingPower.TEN_SECONDS_MS, nowEpochMs)?.let { put("power_10s", it) }
                rollingPower.average(RollingPower.THIRTY_SECONDS_MS, nowEpochMs)?.let { put("power_30s", it) }
                rollingPower.normalised()?.let { put("power_normalized", it) }
                putAll(
                    aggregates.snapshot(
                        distanceMeters = track.distanceMeters,
                        movingSeconds = moving,
                        maxSpeedMps = track.maxSpeedMps,
                    ),
                )
            }
        // Measurements keep the built-in origin, so a connected sensor still owns
        // the field it measures. Statistics are marked as worked out: nothing
        // measures an average, so nothing can take one over.
        return values.mapValues {
            val origin = if (it.key in MEASURED) SensorOrigin.BUILT_IN else SensorOrigin.DERIVED
            SensorReading(it.value, origin, nowEpochMs)
        }
    }

    /**
     * Works out the sun's timetable when it is worth working out again.
     *
     * A position is needed and there may not be one for the first minute of a
     * ride, so this is driven by samples arriving rather than by the start:
     * "recompute at the start" would mean recomputing before the watch knows
     * where it is, which is no answer at all.
     */
    private fun refreshSolar(
        latitudeDeg: Double?,
        longitudeDeg: Double?,
        nowEpochMs: Long,
    ) {
        if (latitudeDeg == null || longitudeDeg == null) return
        // Asked of the clock, not of the answer: north of the Arctic circle in
        // June there is no sunrise, and a null result is the right one. Treating
        // it as "not worked out yet" would recompute it every second for the
        // whole ride, which is the one place this arithmetic could cost
        // anything.
        val due = solarAtEpochMs == 0L || nowEpochMs - solarAtEpochMs >= SOLAR_REFRESH_MS
        if (!due) return
        solarAtEpochMs = nowEpochMs
        // Null where the sun does not rise or set that day, which is a real
        // answer this far north and the field shows as blank.
        solar = runCatching { SolarTimes.at(latitudeDeg, longitudeDeg, nowEpochMs) }.getOrNull()
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val TAG = "RecordingViewModel"
        const val MILLIS_PER_SECOND = 1000.0

        /** Half an hour, which is far more often than a sunset moves. */
        const val SOLAR_REFRESH_MS = 30 * 60 * 1000L
        const val MILLIS_PER_MINUTE = 60_000
        const val TICK_MS = 1000L

        /**
         * The fields in [derived] that are measurements rather than statistics,
         * and so still lose to a sensor that measures the same thing.
         */
        val MEASURED = setOf("distance_total", "speed_current", "altitude")

        /**
         * The platform's readings that are totals rather than measurements of
         * now, and so have to be counted from the start of this ride.
         */
        val PLATFORM_TOTALS = setOf("distance_total", "calories", "ascent_total")

        /**
         * The services a finished workout is owed to. Saving with an empty set
         * would make the retention policy consider every ride settled the moment
         * it was written, and evict rides that had never been uploaded.
         */
        val CONNECTORS = setOf("garmin", "strava")
    }
}
