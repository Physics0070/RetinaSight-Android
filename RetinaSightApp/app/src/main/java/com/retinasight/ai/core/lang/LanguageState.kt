package com.retinasight.ai.core.lang

/**
 * Distinguishes "still reading the saved preference" from "the user has never
 * chosen a language".
 *
 * Without this the app would flash the language picker on every cold start
 * before the stored value arrived - confusing, and it would look broken.
 */
sealed interface LanguageState {
    data object Loading : LanguageState
    data object NotChosen : LanguageState
    data class Chosen(val language: AppLanguage) : LanguageState
}
