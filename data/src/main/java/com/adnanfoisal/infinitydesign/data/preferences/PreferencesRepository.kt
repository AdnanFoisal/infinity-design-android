package com.adnanfoisal.infinitydesign.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User preferences. Section 31: selected provider, theme, editor defaults,
 * provider config (URLs/model names — NOT secrets).
 *
 * API keys are deliberately NOT stored here — they live in the user's IME
 * settings / BYOK config on the server side. The app stores only non-secret
 * config: provider URL, model name, default aspect.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "infinity_prefs")

class PreferencesRepository(private val context: Context) {

    object Keys {
        val PROVIDER = stringPreferencesKey("provider")        // "gemini" or "litellm"
        val LITELLM_URL = stringPreferencesKey("litellm_url")  // non-secret
        val LITELLM_MODEL = stringPreferencesKey("litellm_model")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val THEME = stringPreferencesKey("theme")              // "system" | "light" | "dark"
        val DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
        val SNAP_TO_GRID = booleanPreferencesKey("snap_to_grid")
        val GRID_SIZE = intPreferencesKey("grid_size")
        val SHOW_GRID = booleanPreferencesKey("show_grid")
    }

    fun observe(): Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            provider = p[Keys.PROVIDER] ?: "gemini",
            litellmUrl = p[Keys.LITELLM_URL] ?: "",
            litellmModel = p[Keys.LITELLM_MODEL] ?: "gpt-4o",
            geminiModel = p[Keys.GEMINI_MODEL] ?: "gemini-3.7-flash",
            theme = p[Keys.THEME] ?: "system",
            defaultAspect = p[Keys.DEFAULT_ASPECT] ?: "portrait-poster",
            snapToGrid = p[Keys.SNAP_TO_GRID] ?: true,
            gridSize = p[Keys.GRID_SIZE] ?: 16,
            showGrid = p[Keys.SHOW_GRID] ?: false,
        )
    }

    suspend fun setProvider(v: String) = context.dataStore.edit { it[Keys.PROVIDER] = v }
    suspend fun setLitellmUrl(v: String) = context.dataStore.edit { it[Keys.LITELLM_URL] = v }
    suspend fun setLitellmModel(v: String) = context.dataStore.edit { it[Keys.LITELLM_MODEL] = v }
    suspend fun setGeminiModel(v: String) = context.dataStore.edit { it[Keys.GEMINI_MODEL] = v }
    suspend fun setTheme(v: String) = context.dataStore.edit { it[Keys.THEME] = v }
    suspend fun setDefaultAspect(v: String) = context.dataStore.edit { it[Keys.DEFAULT_ASPECT] = v }
    suspend fun setSnapToGrid(v: Boolean) = context.dataStore.edit { it[Keys.SNAP_TO_GRID] = v }
    suspend fun setGridSize(v: Int) = context.dataStore.edit { it[Keys.GRID_SIZE] = v }
    suspend fun setShowGrid(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_GRID] = v }
}

data class UserPreferences(
    val provider: String,
    val litellmUrl: String,
    val litellmModel: String,
    val geminiModel: String,
    val theme: String,
    val defaultAspect: String,
    val snapToGrid: Boolean,
    val gridSize: Int,
    val showGrid: Boolean,
)
