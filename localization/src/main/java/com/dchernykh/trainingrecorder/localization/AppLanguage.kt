package com.dchernykh.trainingrecorder.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages the app is translated into, and how to switch between them.
 *
 * Applied through [AppCompatDelegate] rather than by swapping a Configuration by
 * hand: the framework then persists the choice, re-applies it after a restart,
 * and hands the same answer to every Activity - none of which a hand-rolled
 * wrapper gets right, and all of which the rider notices when it is missing.
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

        fun apply(tag: String?) {
            val language = byTag(tag)
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                },
            )
        }
    }
}
