package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.dchernykh.trainingrecorder.core.connector.CredentialField
import com.dchernykh.trainingrecorder.core.connector.GarminProtocol
import com.dchernykh.trainingrecorder.core.connector.StravaProtocol
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.mobile.connect.AuthorizationResult
import com.dchernykh.trainingrecorder.mobile.connect.GarminAuthResult
import com.dchernykh.trainingrecorder.mobile.connect.GarminAuthorization
import com.dchernykh.trainingrecorder.mobile.connect.StravaAuthorization
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.sync.SettingsPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A service the rider can connect, and the fields its setup screen shows. */
data class ConnectorSetup(
    val id: String,
    val credentialFields: List<CredentialField>,
)

/**
 * Connecting the rider's services: what they type, what is sent to sign in, and
 * what the watch is allowed to be told.
 *
 * Split out of [CompanionViewModel] because the two have nothing to say to each
 * other. Screen layouts, race identifiers and units are edited and published on
 * every keystroke; a credential is held back until the rider asks to connect,
 * runs a multi-step sign-in that can pause for a code from another device, and
 * is then published in a redacted form. Keeping them in one class meant one
 * object holding two unrelated lifecycles.
 *
 * Not itself a view model: it holds no scope and launches nothing. The calls
 * that reach the network suspend, and the view model runs them on its own scope.
 */
class ServiceConnections(
    private val store: PhoneSettingsStore,
    private val publisher: SettingsPublisher,
    private val strava: StravaAuthorization,
    private val garmin: GarminAuthorization,
) {
    /**
     * What each service needs the rider to type. Taken from the protocols rather
     * than from the connectors: the phone collects credentials and never uploads
     * anything, so it has no business holding a transport.
     */
    val connectors: List<ConnectorSetup> =
        listOf(
            // Strava's client id and secret are all the rider types; the access
            // token is obtained by the authorization and never entered.
            ConnectorSetup(StravaProtocol.ID, StravaProtocol.credentialFields),
            // Garmin takes the rider's own login and password. Its sign-in is an
            // undocumented SSO exchange, but it is one the app now performs
            // rather than asking the rider to obtain a token by hand.
            ConnectorSetup(GarminProtocol.ID, GarminProtocol.credentialFields),
        )

    /**
     * Empty until [load] fills it in. Reading and decoding the file where the
     * initializer stands would put it on the main thread during the first frame,
     * and the screen that needs it is four taps away.
     */
    private val credentials: MutableState<Map<String, Map<String, String>>> = mutableStateOf(emptyMap())

    /** Reads what the rider has already entered, off the main thread. */
    suspend fun load() {
        val stored = withContext(Dispatchers.IO) { runCatching { store.readCredentials() }.getOrDefault(emptyMap()) }
        // Merged under what has been typed since, so a slow read cannot undo an
        // edit the rider made while it was happening.
        credentials.value = stored + credentials.value
    }

    /**
     * Which connector last reported something, and what. Connecting opens a
     * browser or waits on an emailed code and comes back minutes later, so a
     * screen that said nothing would leave the rider wondering whether the
     * button worked.
     */
    private val _status = mutableStateOf<Pair<String, Int>?>(null)
    val status: State<Pair<String, Int>?> = _status

    /**
     * Whether Garmin is waiting for a verification code. Drives one extra field
     * on the setup screen rather than a dialog, so the rider can leave the app
     * to go and read their email and find it still there.
     */
    private val _codeRequested = mutableStateOf(false)
    val codeRequested: State<Boolean> = _codeRequested

    fun statusFor(connectorId: String): Int? = _status.value?.takeIf { it.first == connectorId }?.second

    fun credentialsFor(connectorId: String): Map<String, String> = credentials.value[connectorId].orEmpty()

    /** Saved on the phone as it is typed, but never published until asked. */
    suspend fun updateCredential(
        connectorId: String,
        key: String,
        value: String,
    ) {
        val fields = credentials.value[connectorId].orEmpty() + (key to value)
        credentials.value = credentials.value + (connectorId to fields)
        withContext(Dispatchers.IO) { runCatching { store.writeCredentials(credentials.value) } }
    }

    /**
     * Connects a service.
     *
     * Only when asked, unlike every other setting. A password typed one
     * character at a time would otherwise be sent on every keystroke, and the
     * watch would spend the typing failing to log in with the prefixes.
     */
    suspend fun connect(connectorId: String) {
        when (connectorId) {
            StravaProtocol.ID -> connectStrava()
            GarminProtocol.ID -> connectGarmin()
            else -> publish()
        }
    }

    private suspend fun connectStrava() {
        val fields = credentialsFor(StravaProtocol.ID)
        val clientId = fields[StravaProtocol.CLIENT_ID].orEmpty()
        val clientSecret = fields[StravaProtocol.CLIENT_SECRET].orEmpty()
        if (clientId.isBlank() || clientSecret.isBlank()) {
            _status.value = StravaProtocol.ID to R.string.connect_needs_application
            return
        }
        _status.value = StravaProtocol.ID to R.string.connect_in_progress
        _status.value =
            StravaProtocol.ID to
            when (val result = strava.authorize(clientId, clientSecret)) {
                is AuthorizationResult.Authorized -> {
                    // The refresh token and expiry travel with the access
                    // token: Strava's tokens last hours, and the watch can
                    // only renew one if it was given something to renew it
                    // with.
                    remember(StravaProtocol.ID, result.tokens)
                    R.string.connect_done
                }

                is AuthorizationResult.Failed -> result.statusRes
            }
    }

    /**
     * Signs in to Garmin with the login and password the rider typed.
     *
     * Two steps whenever Garmin decides to challenge: the code arrives by email
     * or text a minute later, and [submitCode] finishes what this starts.
     */
    private suspend fun connectGarmin() {
        val fields = credentialsFor(GarminProtocol.ID)
        val login = fields[GarminProtocol.LOGIN].orEmpty()
        val password = fields[GarminProtocol.PASSWORD].orEmpty()
        if (login.isBlank() || password.isBlank()) {
            _status.value = GarminProtocol.ID to R.string.connect_needs_login
            return
        }
        _status.value = GarminProtocol.ID to R.string.connect_in_progress
        // A fresh sign-in throws away the session the last one built, so a code
        // field still standing from that attempt would post against a cookie jar
        // that no longer exists.
        _codeRequested.value = false
        apply(garmin.signIn(login, password))
    }

    /** The second factor, when Garmin asked for one. */
    suspend fun submitCode(code: String) {
        if (code.isBlank()) return
        _status.value = GarminProtocol.ID to R.string.connect_in_progress
        apply(garmin.submitCode(code))
    }

    private suspend fun apply(result: GarminAuthResult) {
        // Raised when Garmin asks, lowered only when it is finally satisfied.
        // Clearing it on a refusal would take the field away the moment the
        // rider mistyped the code, leaving them to start the whole sign-in
        // again to be sent another one.
        if (result is GarminAuthResult.NeedsCode) _codeRequested.value = true
        if (result is GarminAuthResult.Authorized) _codeRequested.value = false
        _status.value =
            GarminProtocol.ID to
            when (result) {
                is GarminAuthResult.Authorized -> {
                    remember(GarminProtocol.ID, result.tokens)
                    R.string.connect_done
                }

                is GarminAuthResult.NeedsCode -> R.string.connect_code_sent
                is GarminAuthResult.WrongCredentials -> R.string.connect_wrong_credentials
                is GarminAuthResult.Failed -> R.string.connect_failed
            }
    }

    private suspend fun remember(
        connectorId: String,
        tokens: Map<String, String>,
    ) {
        tokens.forEach { (key, value) -> updateCredential(connectorId, key, value) }
        publish()
    }

    private suspend fun publish() {
        withContext(Dispatchers.IO) { runCatching { publisher.publishCredentials(publishable()) } }
    }

    /**
     * What the watch is allowed to see.
     *
     * The Garmin login and password are not part of it. The watch uploads with a
     * token and has no use for the password, and a password copied onto a second
     * device is a second device it can be taken from - which is the whole reason
     * the sign-in happens on the phone.
     */
    private fun publishable(): Map<String, Map<String, String>> =
        credentials.value.mapValues { (connectorId, fields) ->
            if (connectorId == GarminProtocol.ID) {
                fields - GarminProtocol.LOGIN - GarminProtocol.PASSWORD
            } else {
                fields
            }
        }
}
