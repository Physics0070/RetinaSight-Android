package com.retinasight.ai.core.lang

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "retinasight_settings")

/**
 * Persists the user's language choice on the device.
 *
 * A null value means "not chosen yet", which is what sends a first-run user to
 * the language picker instead of guessing for them.
 */
class LanguagePreferences(private val context: Context) {

    private val languageKey = stringPreferencesKey("language_tag")

    /** Emits null until the user has explicitly picked a language. */
    val selectedLanguage: Flow<AppLanguage?> =
        context.settingsDataStore.data.map { prefs ->
            prefs[languageKey]?.let { AppLanguage.fromTag(it) }
        }

    /**
     * Same value, but able to say "still loading" - so the UI never flashes the
     * language picker before the stored choice has been read.
     */
    val languageState: Flow<LanguageState> =
        context.settingsDataStore.data.map { prefs ->
            val tag = prefs[languageKey]
            if (tag == null) LanguageState.NotChosen
            else LanguageState.Chosen(AppLanguage.fromTag(tag))
        }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { prefs ->
            prefs[languageKey] = language.tag
        }
    }
}
