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
enum class AppLanguage(val tag: String, val endonym: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    MARATHI("mr", "मराठी"),
    TAMIL("ta", "தமிழ்"),
    TELUGU("te", "తెలుగు"),
    KANNADA("kn", "ಕನ್ನಡ"),
    BENGALI("bn", "বাংলা"),
    GUJARATI("gu", "ગુજરાતી"),
    MALAYALAM("ml", "മലയാളം"),
    PUNJABI("pa", "ਪੰਜਾਬੀ"),
    ODIA("or", "ଓଡ଼ିଆ");

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
