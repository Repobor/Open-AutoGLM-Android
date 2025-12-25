package io.repobor.autoglm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Execution mode for task automation.
 */
enum class ExecutionMode(val value: String) {
    ACCESSIBILITY("accessibility"),
    SHIZUKU("shizuku");

    companion object {
        fun fromValue(value: String): ExecutionMode {
            return entries.find { it.value == value } ?: ACCESSIBILITY
        }
    }
}

/**
 * Repository for managing app settings using DataStore.
 * Provides persistent storage for API configuration and user preferences.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
            name = "autoglm_settings"
        )

        // Preference keys
        private val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL_NAME = stringPreferencesKey("model_name")
        private val MAX_STEPS = intPreferencesKey("max_steps")
        private val LANGUAGE = stringPreferencesKey("language")
        private val TEMPERATURE = floatPreferencesKey("temperature")
        private val EXECUTION_MODE = stringPreferencesKey("execution_mode")
        private val FLOATING_WINDOW_X = intPreferencesKey("floating_window_x")
        private val FLOATING_WINDOW_Y = intPreferencesKey("floating_window_y")

        // Default values
        const val DEFAULT_API_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4"
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_MODEL_NAME = "autoglm-phone"
        const val DEFAULT_MAX_STEPS = 30
        const val DEFAULT_LANGUAGE = "zh"
        const val DEFAULT_TEMPERATURE = 0.0f
        val DEFAULT_EXECUTION_MODE = ExecutionMode.ACCESSIBILITY
    }

    /**
     * Flow of API endpoint setting.
     */
    val apiEndpoint: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[API_ENDPOINT] ?: DEFAULT_API_ENDPOINT
        }

    /**
     * Flow of API key setting.
     */
    val apiKey: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[API_KEY] ?: DEFAULT_API_KEY
        }

    /**
     * Flow of model name setting.
     */
    val modelName: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[MODEL_NAME] ?: DEFAULT_MODEL_NAME
        }

    /**
     * Flow of max steps setting.
     */
    val maxSteps: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[MAX_STEPS] ?: DEFAULT_MAX_STEPS
        }

    /**
     * Flow of language setting.
     */
    val language: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LANGUAGE] ?: DEFAULT_LANGUAGE
        }

    /**
     * Flow of temperature setting.
     */
    val temperature: Flow<Float> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE
        }

    /**
     * Flow of execution mode setting.
     */
    val executionMode: Flow<ExecutionMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val value = preferences[EXECUTION_MODE] ?: DEFAULT_EXECUTION_MODE.value
            ExecutionMode.fromValue(value)
        }

    /**
     * Flow of floating window X position setting.
     */
    val floatingWindowX: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FLOATING_WINDOW_X] ?: 100
        }

    /**
     * Flow of floating window Y position setting.
     */
    val floatingWindowY: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FLOATING_WINDOW_Y] ?: 100
        }

    /**
     * Save API endpoint setting.
     */
    suspend fun saveApiEndpoint(url: String) {
        context.dataStore.edit { preferences ->
            preferences[API_ENDPOINT] = url
        }
    }

    /**
     * Save API key setting.
     */
    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    /**
     * Save model name setting.
     */
    suspend fun saveModelName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = name
        }
    }

    /**
     * Save max steps setting.
     */
    suspend fun saveMaxSteps(steps: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_STEPS] = steps
        }
    }

    /**
     * Save language setting.
     */
    suspend fun saveLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = lang
        }
    }

    /**
     * Save temperature setting.
     */
    suspend fun saveTemperature(temp: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE] = temp
        }
    }

    /**
     * Save execution mode setting.
     */
    suspend fun saveExecutionMode(mode: ExecutionMode) {
        context.dataStore.edit { preferences ->
            preferences[EXECUTION_MODE] = mode.value
        }
    }

    /**
     * Save floating window X position setting.
     */
    suspend fun saveFloatingWindowX(x: Int) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_WINDOW_X] = x
        }
    }

    /**
     * Save floating window Y position setting.
     */
    suspend fun saveFloatingWindowY(y: Int) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_WINDOW_Y] = y
        }
    }

    /**
     * Save floating window position.
     */
    suspend fun saveFloatingWindowPosition(x: Int, y: Int) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_WINDOW_X] = x
            preferences[FLOATING_WINDOW_Y] = y
        }
    }

    /**
     * Save all settings at once.
     */
    suspend fun saveAllSettings(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        maxSteps: Int,
        language: String,
        temperature: Float = DEFAULT_TEMPERATURE
    ) {
        context.dataStore.edit { preferences ->
            preferences[API_ENDPOINT] = apiEndpoint
            preferences[API_KEY] = apiKey
            preferences[MODEL_NAME] = modelName
            preferences[MAX_STEPS] = maxSteps
            preferences[LANGUAGE] = language
            preferences[TEMPERATURE] = temperature
        }
    }

    /**
     * Clear all settings (reset to defaults).
     */
    suspend fun clearAllSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Get all settings as a data class.
     */
    data class Settings(
        val apiEndpoint: String = DEFAULT_API_ENDPOINT,
        val apiKey: String = DEFAULT_API_KEY,
        val modelName: String = DEFAULT_MODEL_NAME,
        val maxSteps: Int = DEFAULT_MAX_STEPS,
        val language: String = DEFAULT_LANGUAGE,
        val temperature: Float = DEFAULT_TEMPERATURE,
        val executionMode: ExecutionMode = DEFAULT_EXECUTION_MODE
    )

    /**
     * Flow of all settings combined.
     */
    val allSettings: Flow<Settings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            Settings(
                apiEndpoint = preferences[API_ENDPOINT] ?: DEFAULT_API_ENDPOINT,
                apiKey = preferences[API_KEY] ?: DEFAULT_API_KEY,
                modelName = preferences[MODEL_NAME] ?: DEFAULT_MODEL_NAME,
                maxSteps = preferences[MAX_STEPS] ?: DEFAULT_MAX_STEPS,
                language = preferences[LANGUAGE] ?: DEFAULT_LANGUAGE,
                temperature = preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE,
                executionMode = ExecutionMode.fromValue(
                    preferences[EXECUTION_MODE] ?: DEFAULT_EXECUTION_MODE.value
                )
            )
        }
}
