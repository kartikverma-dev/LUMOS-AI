package com.lumos.ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lumos_settings")

data class LumosSettings(
    val serverUrl: String = "http://127.0.0.1:8080",
    val modelName: String = "dolphin-3b-iq4_xs.gguf",
    val systemPrompt: String = "You are LUMOS, a helpful local AI assistant running on-device.",
    val temperature: Float = 0.7f,
    val contextSize: Int = 4096,
    val cpuThreads: Int = 4,
    val gpuLayers: Int = 0
)

class SettingsRepository(private val context: Context) {

    private val KEY_SERVER = stringPreferencesKey("server_url")
    private val KEY_MODEL = stringPreferencesKey("model_name")
    private val KEY_SYSPROMPT = stringPreferencesKey("system_prompt")
    private val KEY_TEMP = floatPreferencesKey("temperature")
    private val KEY_CTX = stringPreferencesKey("context_size")
    private val KEY_THREADS = stringPreferencesKey("cpu_threads")
    private val KEY_GPU = stringPreferencesKey("gpu_layers")

    val settings: Flow<LumosSettings> = context.dataStore.data.map { p ->
        LumosSettings(
            serverUrl = p[KEY_SERVER] ?: "http://127.0.0.1:8080",
            modelName = p[KEY_MODEL] ?: "dolphin-3b-iq4_xs.gguf",
            systemPrompt = p[KEY_SYSPROMPT]
                ?: "You are LUMOS, a helpful local AI assistant running on-device.",
            temperature = p[KEY_TEMP] ?: 0.7f,
            contextSize = (p[KEY_CTX] ?: "4096").toIntOrNull() ?: 4096,
            cpuThreads = (p[KEY_THREADS] ?: "4").toIntOrNull() ?: 4,
            gpuLayers = (p[KEY_GPU] ?: "0").toIntOrNull() ?: 0
        )
    }

    suspend fun setServerUrl(v: String) = context.dataStore.edit { it[KEY_SERVER] = v }
    suspend fun setModelName(v: String) = context.dataStore.edit { it[KEY_MODEL] = v }
    suspend fun setSystemPrompt(v: String) = context.dataStore.edit { it[KEY_SYSPROMPT] = v }
    suspend fun setTemperature(v: Float) = context.dataStore.edit { it[KEY_TEMP] = v }
    suspend fun setContextSize(v: Int) = context.dataStore.edit { it[KEY_CTX] = v.toString() }
    suspend fun setCpuThreads(v: Int) = context.dataStore.edit { it[KEY_THREADS] = v.toString() }
    suspend fun setGpuLayers(v: Int) = context.dataStore.edit { it[KEY_GPU] = v.toString() }
}
