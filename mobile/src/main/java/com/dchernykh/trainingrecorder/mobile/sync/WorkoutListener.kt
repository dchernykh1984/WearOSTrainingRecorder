package com.dchernykh.trainingrecorder.mobile.sync

import android.content.Context
import com.dchernykh.trainingrecorder.core.datalayer.WorkoutSummaryContract
import com.dchernykh.trainingrecorder.core.workout.WorkoutSummary
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Receives the workout list the watch publishes.
 *
 * A [WearableListenerService] rather than a listener on the Activity, for the
 * same reason the watch uses one for the settings: the watch pushes when a ride
 * finishes or an upload settles, which is exactly when the rider is *not*
 * looking at the phone. A history that only updated while the app happened to be
 * open would be empty every time it was opened.
 */
class WorkoutListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.dataItem.uri.path != WorkoutSummaryContract.PATH) return@forEach
            DataMapItem
                .fromDataItem(event.dataItem)
                .dataMap
                .getString(WorkoutSummaryContract.KEY_PAYLOAD)
                ?.let { WorkoutHistoryStore(this).write(it) }
        }
    }
}

/**
 * The last list the watch sent, kept on disk.
 *
 * Kept at all because the alternative is a screen that is blank until the watch
 * happens to say something: the Data Layer delivers on its own schedule, and a
 * rider who opens the phone on the train should see the ride they did this
 * morning rather than a spinner.
 *
 * A payload the contract refuses is not stored, which is what lets an older
 * phone keep working next to a watch that has been updated past it.
 */
class WorkoutHistoryStore(
    context: Context,
    private val file: File = File(context.filesDir, "workouts.json"),
) {
    fun write(payload: String) {
        if (WorkoutSummaryContract.decode(payload) == null) return
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(payload)
        // Replaced atomically: a phone killed mid-write would otherwise leave a
        // half-file that reads back as no history at all.
        temporary.renameTo(file)
    }

    fun read(): List<WorkoutSummary> =
        if (file.exists()) {
            runCatching { WorkoutSummaryContract.decode(file.readText()) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

    companion object {
        /**
         * Pulls whatever the watch last published.
         *
         * Needed because the listener only hears *changes*. A phone installed or
         * reinstalled after the rides were recorded would otherwise show nothing
         * until the next ride finished, which reads as the history being broken.
         */
        suspend fun fetchExisting(context: Context): String? =
            runCatching {
                Wearable
                    .getDataClient(context)
                    .dataItems
                    .await()
                    .firstOrNull { it.uri.path == WorkoutSummaryContract.PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap.getString(WorkoutSummaryContract.KEY_PAYLOAD) }
            }.getOrNull()
    }
}
