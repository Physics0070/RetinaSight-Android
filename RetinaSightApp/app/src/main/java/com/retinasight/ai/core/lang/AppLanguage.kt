package com.retinasight.ai.core.lang

import java.util.Locale

/**
 * The languages the app ships in.
 *
 * [endonym] is intentionally NOT a translatable resource: the language picker
 * must show every language in its own script at the same time, whatever the
 * current locale is. A user who cannot read English must still be able to find
 * their own language on that screen.
 */
enum class AppLanguage(
    val tag: String,
    val endonym: String,
    /**
     * The Unicode block this language is written in, or null for Latin.
     *
     * Used to check that generated text is actually in the language that was
     * asked for. A small model asked for Marathi will happily answer in
     * English, and a byte-encoding fault produces Latin-1 punctuation - both
     * look like success to a length check and neither is readable to the
     * patient.
     */
    val scriptRange: IntRange?
) {
    ENGLISH("en", "English", null),
    HINDI("hi", "हिन्दी", 0x0900..0x097F),
    MARATHI("mr", "मराठी", 0x0900..0x097F),
    TAMIL("ta", "தமிழ்", 0x0B80..0x0BFF),
    TELUGU("te", "తెలుగు", 0x0C00..0x0C7F),
    KANNADA("kn", "ಕನ್ನಡ", 0x0C80..0x0CFF),
    BENGALI("bn", "বাংলা", 0x0980..0x09FF),
    GUJARATI("gu", "ગુજરાતી", 0x0A80..0x0AFF),
    MALAYALAM("ml", "മലയാളം", 0x0D00..0x0D7F),
    PUNJABI("pa", "ਪੰਜਾਬੀ", 0x0A00..0x0A7F),
    ODIA("or", "ଓଡ଼ିଆ", 0x0B00..0x0B7F);

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        val DEFAULT = ENGLISH

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: DEFAULT

        /**
         * Best match for the phone's own locale, so the picker can pre-highlight
         * a sensible option instead of defaulting to English for everyone.
         */
        fun fromSystem(): AppLanguage {
            val systemTag = Locale.getDefault().language
            return entries.firstOrNull { it.tag == systemTag } ?: DEFAULT
        }
    }
}
