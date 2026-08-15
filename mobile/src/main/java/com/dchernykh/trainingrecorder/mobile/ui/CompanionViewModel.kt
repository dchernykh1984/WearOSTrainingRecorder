package com.dchernykh.trainingrecorder.mobile.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.core.config.ConfigTarget
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.config.reset
import com.dchernykh.trainingrecorder.core.config.resolve
import com.dchernykh.trainingrecorder.core.config.withScreensFor
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.mobile.connect.GarminAuthorization
import com.dchernykh.trainingrecorder.mobile.connect.StravaAuthorization
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SettingsPublisher
import com.dchernykh.trainingrecorder.mobile.sync.WorkoutHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The five places the companion app can be. */
enum class Section(
    val labelRes: Int,
) {
    SPORTS(R.string.nav_sports),
    RACE(R.string.nav_race),
    CONNECTIONS(R.string.nav_connections),
    HISTORY(R.string.nav_history),
    SETTINGS(R.string.nav_settings),
}

/**
 * Holds what the companion app is editing, and pushes it to the watch.
 *
 * Every change is saved and published immediately rather than behind a Save
 * button. There is no draft worth losing here - each edit is one field in one
 * slot - and a rider who changed a screen on the sofa expects it on the watch
 * by the time they are at the door.
 */
class CompanionViewModel(
    application: Application,
    private val store: PhoneSettingsStore = PhoneSettingsStore(application),
    private val publisher: SettingsPublisher = SettingsPublisher(application),
    private val history: WorkoutHistoryStore = WorkoutHistoryStore(application),
    /** Credentials and sign-ins, which have their own lifecycle entirely. */
    val connections: ServiceConnections =
        ServiceConnections(store, publisher, StravaAuthorization(application), GarminAuthorization()),
) : AndroidViewModel(application) {
    /**
     * Kotlin default arguments do not emit a one-argument constructor, and
     * `viewModel()` reflects for exactly that signature - without this the app
     * dies on its first frame with "cannot create an instance".
     */
    constructor(application: Application) : this(
        application,
        PhoneSettingsStore(application),
        SettingsPublisher(application),
        WorkoutHistoryStore(application),
    )

    private val _configuration = mutableStateOf(ScreenConfiguration.initial())
    val configuration: State<ScreenConfiguration> = _configuration

    private val _race = mutableStateOf(RaceStatsConfig())
    val race: State<RaceStatsConfig> = _race

    private val _units = mutableStateOf(UnitSystem.METRIC)
    val units: State<UnitSystem> = _units

    private val _language = mutableStateOf(AppLanguage.SYSTEM)
    val language: State<AppLanguage> = _language

    /**
     * The phone shows the history but does not own it: the workouts live on the
     * watch, and this is the last list it published. Empty means "the watch has
     * not told us anything yet", not "you have never ridden".
     */
    private val _workouts = mutableStateOf<List<WorkoutSummary>>(emptyList())
    val workouts: State<List<WorkoutSummary>> = _workouts

    init {
        store.readSettings()?.let {
            _configuration.value = it.screens
            _race.value = it.race
            _units.value = it.units
            _language.value = AppLanguage.byTag(it.languageTag)
        }
        // From disk first, so the screen has something before the Data Layer
        // gets round to answering - but off the main thread, because reading and
        // decoding fifty rides during the first frame is jank the rider sees.
        viewModelScope.launch {
            _workouts.value = withContext(Dispatchers.IO) { history.read() }
            refreshWorkouts()
            connections.load()
        }
    }

    /**
     * Asks the Data Layer for the watch's current list.
     *
     * Needed alongside the listener because the listener only hears *changes*: a
     * phone reinstalled after a season of rides would otherwise show nothing
     * until the next ride finished.
     */
    fun refreshWorkouts() {
        viewModelScope.launch {
            WorkoutHistoryStore.fetchExisting(getApplication())?.let { payload ->
                withContext(Dispatchers.IO) { history.write(payload) }
            }
            _workouts.value = withContext(Dispatchers.IO) { history.read() }
        }
    }

    fun updateScreens(
        target: ConfigTarget,
        screens: ScreenSet,
    ) {
        // Forked on the first edit: changing one tier must not quietly rewrite
        // every other tier that was reading the same inherited screens.
        _configuration.value = _configuration.value.withScreensFor(target, screens)
        persist()
    }

    fun assignField(
        target: ConfigTarget,
        screenIndex: Int,
        slotIndex: Int,
        fieldId: String?,
    ) {
        val current = _configuration.value.resolve(target)
        val screen = current.screens.getOrNull(screenIndex) ?: return
        updateScreens(target, current.withScreen(screenIndex, screen.withSlot(slotIndex, fieldId)))
    }

    fun resetTarget(target: ConfigTarget) {
        _configuration.value = _configuration.value.reset(target)
        persist()
    }

    fun updateRace(config: RaceStatsConfig) {
        _race.value = config
        persist()
    }

    /**
     * Metric or imperial, for every field at once.
     *
     * Chosen on the phone and carried to the watch with the rest of the settings,
     * so the two never disagree - a watch still counting kilometres after the
     * rider switched the phone to miles reads as a bug, not as two settings.
     */
    fun updateUnits(system: UnitSystem) {
        _units.value = system
        persist()
    }

    /** Persisted here; the Activity applies it by recreating itself. */
    fun updateLanguage(language: AppLanguage) {
        _language.value = language
        persist()
    }

    /** Runs a sign-in on the model's scope; the work itself is [connections]'. */
    fun connect(connectorId: String) {
        viewModelScope.launch { connections.connect(connectorId) }
    }

    fun submitGarminCode(code: String) {
        viewModelScope.launch { connections.submitCode(code) }
    }

    fun updateCredential(
        connectorId: String,
        key: String,
        value: String,
    ) {
        viewModelScope.launch { connections.updateCredential(connectorId, key, value) }
    }

    private fun persist() {
        val settings =
            WatchSettings(
                screens = _configuration.value,
                race = _race.value,
                units = _units.value,
                languageTag = AppLanguage.tagOf(_language.value),
            )
        save {
            store.writeSettings(settings)
            publisher.publish(settings)
        }
    }

    /**
     * Off the main thread. Every keystroke in a text field lands here, and a
     * file write plus a Data Layer put per character is jank at best and an ANR
     * on a slow phone.
     */
    private fun save(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching(block) }
    }
}
