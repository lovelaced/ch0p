package io.ch0p

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ch0p_settings")

/**
 * Remembered preferences so the app "just works" without re-choosing each clip. Sensible
 * defaults; last choice sticks. Backed by DataStore.
 *
 *  - transcription: "auto" (best installed model) | "off" | a specific model id
 *  - format: "MP4" | "WEBM"
 *  - defaultPreset: a preset id (the carousel opens here)
 */
class SettingsStore(private val context: Context) {

    val transcription: Flow<String> = context.dataStore.data.map { it[KEY_TRANSCRIPTION] ?: "auto" }
    val format: Flow<String> = context.dataStore.data.map { it[KEY_FORMAT] ?: "MP4" }
    val defaultPreset: Flow<String> = context.dataStore.data.map { it[KEY_PRESET] ?: "shortform" }

    suspend fun setTranscription(value: String) = put(KEY_TRANSCRIPTION, value)
    suspend fun setFormat(value: String) = put(KEY_FORMAT, value)
    suspend fun setDefaultPreset(value: String) = put(KEY_PRESET, value)

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_TRANSCRIPTION = stringPreferencesKey("transcription")
        val KEY_FORMAT = stringPreferencesKey("format")
        val KEY_PRESET = stringPreferencesKey("default_preset")
    }
}
