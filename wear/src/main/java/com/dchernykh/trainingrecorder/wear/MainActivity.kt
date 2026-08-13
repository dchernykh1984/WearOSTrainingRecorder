package com.dchernykh.trainingrecorder.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dchernykh.trainingrecorder.wear.ui.TrainingRecorderApp

/** The watch app's single entry point. Everything below it is Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrainingRecorderApp() }
    }
}
