package com.dchernykh.trainingrecorder.wear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.wear.sync.SettingsStore
import com.dchernykh.trainingrecorder.wear.ui.TrainingRecorderApp
import com.dchernykh.trainingrecorder.wear.upload.UploadWorker

/** The watch app's single entry point. Everything below it is Compose. */
class MainActivity : ComponentActivity() {
    /**
     * The chosen language is applied here rather than in onCreate: resources are
     * resolved from the base context, so anything later is already too late for
     * the first screen the rider sees.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase, SettingsStore(newBase).read()?.languageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrainingRecorderApp() }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is the cheapest moment to notice a backlog: the rider
        // is looking at the watch, and it is awake and probably on Wi-Fi.
        UploadWorker.schedule(this)
    }
}
