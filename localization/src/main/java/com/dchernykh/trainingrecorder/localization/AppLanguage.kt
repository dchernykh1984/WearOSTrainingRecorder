package com.dchernykh.trainingrecorder.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The languages the app is translated into, and how to switch between them.
 *
 * Applied by wrapping the Activity's base context rather than through
 * `AppCompatDelegate.setApplicationLocales`. That API only reaches the framework
 * below API 33 through an AppCompatActivity, and both apps are Compose on a
 * plain ComponentActivity - so on Android 8 to 12 phones and every Wear OS 3
 * watch the setting would move and nothing on screen would change. The choice is
 * already persisted with the rest of the settings, so appcompat's own storage
 * would only be a second place for it to disagree from.
 */
enum class AppLanguage(
    val tag: String,
    val labelRes: Int,
) {
    /** Whatever the device is set to. The default, because it is usually right. */
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_en),
    RUSSIAN("ru", R.string.language_ru),
    KAZAKH("kk", R.string.language_kk),
    SPANISH("es", R.string.language_es),
    CHINESE("zh", R.string.language_zh),
    HINDI("hi", R.string.language_hi),
    PORTUGUESE("pt", R.string.language_pt),
    FRENCH("fr", R.string.language_fr),
    GERMAN("de", R.string.language_de),
    JAPANESE("ja", R.string.language_ja),
    ITALIAN("it", R.string.language_it),
    TURKISH("tr", R.string.language_tr),
    POLISH("pl", R.string.language_pl),
    DUTCH("nl", R.string.language_nl),
    CZECH("cs", R.string.language_cs),
    ;

    companion object {
        /**
         * The language for a stored tag. An unknown tag - a newer phone naming a
         * language this build does not carry - falls back to [SYSTEM] rather
         * than to English: the device language is a better guess than ours.
         */
        fun byTag(tag: String?): AppLanguage =
            if (tag.isNullOrBlank()) SYSTEM else entries.firstOrNull { it.tag == tag } ?: SYSTEM

        /** What [SYSTEM] is stored as: absent, not a tag. */
        fun tagOf(language: AppLanguage): String? = language.tag.ifBlank { null }

        /**
         * The context an Activity should run on, given the chosen language.
         *
         * Returns the original for [SYSTEM], so the device language keeps
         * following the device - including when the rider changes it.
         */
        fun wrap(
            context: Context,
            tag: String?,
        ): Context {
            val language = byTag(tag)
            if (language == SYSTEM) return context
            val locale = Locale.forLanguageTag(language.tag)
            Locale.setDefault(locale)
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            return context.createConfigurationContext(configuration)
        }
    }
}
