package com.dchernykh.trainingrecorder.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Placeholder entry point for the phone companion. Replaced once the Strava /
 * Garmin Connect authorization and sync screens land.
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
