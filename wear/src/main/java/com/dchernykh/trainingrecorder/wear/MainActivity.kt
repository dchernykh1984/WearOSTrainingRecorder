package com.dchernykh.trainingrecorder.wear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.wear.sync.SettingsStore
import com.dchernykh.trainingrecorder.wear.ui.TrainingRecorderApp
import com.dchernykh.trainingrecorder.wear.upload.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The watch app's single entry point. Everything below it is Compose. */
class MainActivity : ComponentActivity() {
    /** What the screen was actually built with, to compare a later push against. */
    private var appliedLanguageTag: String? = null

    /**
     * The chosen language is applied here rather than in onCreate: resources are
     * resolved from the base context, so anything later is already too late for
     * the first screen the rider sees.
     */
    override fun attachBaseContext(newBase: Context) {
        appliedLanguageTag = SettingsStore(newBase).read()?.languageTag
        super.attachBaseContext(AppLanguage.wrap(newBase, appliedLanguageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrainingRecorderApp() }
        watchForLanguageChanges()
    }

    /**
     * Rebuilds the screen when the phone sends a different language.
     *
     * Because the language is read as the Activity is built, a push that arrived
     * afterwards reached the file and stopped there - the rider changed the
     * language on the phone and the watch went on in the old one until something
     * else happened to restart it. Everything else the phone sends is observed;
     * this was the one setting that needed the screen rebuilt to take effect, and
     * so the one that appeared not to arrive.
     *
     * Only on a real change: recreating costs the rider a blink, which is
     * nothing once in a while and wrong on every settings push. A recording is
     * unaffected - it lives in the service and a view model, and both outlive
     * this.
     */
    private fun watchForLanguageChanges() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsStore.revision.collect {
                    // Guarded: this reads a file, and an unreadable one would
                    // otherwise take the exception up through the lifecycle
                    // scope and end the app - a spectacular way to fail at
                    // noticing a language change.
                    //
                    // The failure is kept inside the Result rather than
                    // flattened to null, because null is also a real answer
                    // here: it is what "follow the system" looks like, and
                    // collapsing the two would leave a rider who switched back
                    // to the system language on the one they chose before.
                    val read =
                        withContext(Dispatchers.IO) {
                            runCatching { SettingsStore(this@MainActivity).read()?.languageTag }
                        }
                    val tag = read.getOrNull()
                    if (read.isSuccess && tag != appliedLanguageTag) recreate()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is the cheapest moment to notice a backlog: the rider
        // is looking at the watch, and it is awake and probably on Wi-Fi.
        UploadWorker.schedule(this)
    }
}
