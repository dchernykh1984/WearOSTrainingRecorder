package com.dchernykh.trainingrecorder.wear

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Placeholder entry point for the watch app. Replaced once the exercise
 * recording screens land.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.app_name)
            },
        )
    }
}
