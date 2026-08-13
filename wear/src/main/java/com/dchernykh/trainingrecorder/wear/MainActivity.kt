package com.dchernykh.trainingrecorder.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.wear.sync.SettingsStore
import com.dchernykh.trainingrecorder.wear.ui.TrainingRecorderApp
import com.dchernykh.trainingrecorder.wear.upload.UploadWorker

/** The watch app's single entry point. Everything below it is Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Applied before the first frame, so the watch never shows one screen in
        // the device language and the next in the one chosen on the phone.
        AppLanguage.apply(SettingsStore(this).read()?.languageTag)
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
