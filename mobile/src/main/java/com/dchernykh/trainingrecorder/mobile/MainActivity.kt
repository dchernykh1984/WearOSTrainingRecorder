package com.dchernykh.trainingrecorder.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.mobile.settings.PhoneSettingsStore
import com.dchernykh.trainingrecorder.mobile.ui.CompanionApp

/** The phone companion's entry point. Everything below it is Compose. */
class MainActivity : ComponentActivity() {
    /**
     * The chosen language is applied here rather than in onCreate: resources are
     * resolved from the base context, so anything later is already too late for
     * the first screen the rider sees. Choosing a new one recreates the Activity,
     * which comes back through here.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase, PhoneSettingsStore(newBase).readSettings()?.languageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompanionApp(onLanguageChanged = ::recreate) }
    }
}
