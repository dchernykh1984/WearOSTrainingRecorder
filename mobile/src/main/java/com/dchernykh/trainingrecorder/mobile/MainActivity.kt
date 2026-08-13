package com.dchernykh.trainingrecorder.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dchernykh.trainingrecorder.mobile.ui.CompanionApp

/** The phone companion's entry point. Everything below it is Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompanionApp() }
    }
}
