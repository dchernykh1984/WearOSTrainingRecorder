package com.dchernykh.trainingrecorder.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dchernykh.trainingrecorder.mobile.ui.CompanionApp

/**
 * The phone companion's entry point. Everything below it is Compose.
 *
 * The chosen language is applied by the view model rather than here: appcompat
 * persists it and re-applies it on every launch, so doing it again in onCreate
 * would only be a second source of truth to disagree with the first.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompanionApp() }
    }
}
