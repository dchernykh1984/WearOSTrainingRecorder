package com.dchernykh.trainingrecorder.wear.sync

import com.dchernykh.trainingrecorder.core.config.ScreenConfiguration
import com.dchernykh.trainingrecorder.core.datalayer.SyncContract
import com.dchernykh.trainingrecorder.core.datalayer.WatchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = SettingsStore(File(folder.root, "settings.json"), File(folder.root, "sport-history.txt"))

    private fun payload(languageTag: String?) =
        SyncContract.encode(
            WatchSettings(screens = ScreenConfiguration.initial(), languageTag = languageTag),
        )

    @Test
    fun aSettingsPushIsAnnouncedSoAScreenCanReactToIt() {
        // The language is read as the screen is built, so a push that only
        // reached the file left the watch in the old language until something
        // restarted it. This signal is what carries it the rest of the way.
        val before = SettingsStore.revision.value
        store().write(payload("en"))
        assertNotEquals(before, SettingsStore.revision.value)
    }

    @Test
    fun aPayloadThatIsRefusedAnnouncesNothing() {
        // Nothing was written, so there is nothing for a screen to go and read -
        // and rebuilding it would cost the rider a blink for no change at all.
        store().write(payload("en"))
        val after = SettingsStore.revision.value
        store().write("{ not json")
        assertEquals(after, SettingsStore.revision.value)
    }

    @Test
    fun theLanguageSurvivesTheRoundTrip() {
        val store = store()
        store.write(payload("ru"))
        assertEquals("ru", store.read()?.languageTag)
        store.write(payload(null))
        assertNull("clearing it means following the system again", store.read()?.languageTag)
    }

    @Test
    fun thereIsNothingToReadBeforeThePhoneHasEverPushed() {
        assertNull(store().read())
    }
}
