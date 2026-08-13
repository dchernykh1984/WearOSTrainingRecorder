package com.dchernykh.trainingrecorder.mobile.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.config.ScreenSet
import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import com.dchernykh.trainingrecorder.core.format.UnitSystem
import com.dchernykh.trainingrecorder.core.race.RaceStatsConfig
import com.dchernykh.trainingrecorder.core.sport.SportType
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SettingsPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

/** A service the rider can connect, and the fields its setup screen shows. */
data class ConnectorSetup(
    val id: String,
    val credentialFields: List<CredentialField>,
)

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
     * The phone shows the history but does not hold it: the workouts are on the
     * watch. Empty until it syncs one over, which is honest - an empty list here
     * means "nothing has arrived", not "you have never ridden".
     */
    private val _workouts = mutableStateOf<List<WorkoutSummary>>(emptyList())
    val workouts: State<List<WorkoutSummary>> = _workouts

    /**
     * What each service needs the rider to type. Taken from the protocols rather
     * than from the connectors: the phone collects credentials and never uploads
     * anything, so it has no business holding a transport.
     */
    val connectors: List<ConnectorSetup> =
        listOf(
            ConnectorSetup(StravaProtocol.ID, StravaProtocol.credentialFields),
            ConnectorSetup(GarminProtocol.ID, GarminProtocol.credentialFields),
        )

    private val credentials: MutableState<Map<String, Map<String, String>>> = mutableStateOf(emptyMap())

    init {
        store.readSettings()?.let {
            _configuration.value = it.screens
            _race.value = it.race
            _units.value = it.units
            _language.value = AppLanguage.byTag(it.languageTag)
        }
        credentials.value = store.readCredentials()
    }

    fun credentialsFor(connectorId: String): Map<String, String> = credentials.value[connectorId].orEmpty()

    fun updateCredential(
        connectorId: String,
        key: String,
        value: String,
    ) {
        val fields = credentials.value[connectorId].orEmpty() + (key to value)
        credentials.value = credentials.value + (connectorId to fields)
        save { store.writeCredentials(credentials.value) }
    }

    /**
     * Sent only when the rider asks, unlike everything else here. A token typed
     * one character at a time would otherwise be published half-finished on
     * every keystroke, and the watch would spend the typing failing to log in.
     */
    fun publishCredentials() {
        save { publisher.publishCredentials(credentials.value) }
    }

    fun updateScreens(
        sport: SportType,
        screens: ScreenSet,
    ) {
        // Forked on the first edit: changing a sport must not quietly rewrite
        // every other sport that shares its discipline's screens.
        _configuration.value = _configuration.value.withScreensFor(sport, screens)
        persist()
    }

    fun assignField(
        sport: SportType,
        screenIndex: Int,
        slotIndex: Int,
        fieldId: String?,
    ) {
        val current = _configuration.value.resolve(sport)
        val screen = current.screens.getOrNull(screenIndex) ?: return
        updateScreens(sport, current.withScreen(screenIndex, screen.withSlot(slotIndex, fieldId)))
    }

    fun resetSport(sport: SportType) {
        _configuration.value = _configuration.value.resetSportType(sport)
        persist()
    }

    fun updateRace(config: RaceStatsConfig) {
        _race.value = config
        persist()
    }

    /** Persisted here; the Activity applies it by recreating itself. */
    fun updateLanguage(language: AppLanguage) {
        _language.value = language
        persist()
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
