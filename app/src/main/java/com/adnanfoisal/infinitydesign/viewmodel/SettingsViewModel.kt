package com.adnanfoisal.infinitydesign.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanfoisal.infinitydesign.core.dispatchers.AppDispatchers
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.data.preferences.PreferencesRepository
import com.adnanfoisal.infinitydesign.data.preferences.UserPreferences
import com.adnanfoisal.infinitydesign.generation.blueprint.PingResponse
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import com.adnanfoisal.infinitydesign.generation.providers.SsrfGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The app talks to the backend over HTTP. Section 34: the backend owns the LLM
 * provider calls — the Android client never holds them directly.
 *
 * Section 38: every request has timeouts. Section 41: graceful failure modes.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    val prefsFlow: StateFlow<UserPreferences> = prefs.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences(
            provider = "gemini", litellmUrl = "", litellmModel = "gpt-4o",
            geminiModel = "gemini-3.7-flash", theme = "system", defaultAspect = "portrait-poster",
            snapToGrid = true, gridSize = 16, showGrid = false,
        ))

    sealed class PingState {
        object Idle : PingState()
        object Testing : PingState()
        data class Ok(val provider: String, val model: String, val durationMs: Long) : PingState()
        data class Failed(val message: String) : PingState()
    }

    private val _pingState = MutableStateFlow<PingState>(PingState.Idle)
    val pingState: StateFlow<PingState> = _pingState.asStateFlow()

    private var pingJob: Job? = null
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun setProvider(v: String) = viewModelScope.launch { prefs.setProvider(v) }
    fun setLitellmUrl(v: String) = viewModelScope.launch { prefs.setLitellmUrl(v) }
    fun setLitellmModel(v: String) = viewModelScope.launch { prefs.setLitellmModel(v) }
    fun setGeminiModel(v: String) = viewModelScope.launch { prefs.setGeminiModel(v) }
    fun setTheme(v: String) = viewModelScope.launch { prefs.setTheme(v) }

    /**
     * Ping/test the configured provider via the backend. Sends a small request
     * and reports whether the credentials are working.
     *
     * The user's API key is sent over HTTPS to the backend, which forwards it
     * to the LLM provider — section 39 BYOK flow. The key is never logged.
     */
    fun ping(
        backendUrl: String,
        kind: ProviderKind,
        geminiApiKey: String,
        litellmUrl: String,
        litellmApiKey: String,
        litellmModel: String,
        geminiModel: String,
    ) {
        pingJob?.cancel()
        _pingState.value = PingState.Testing
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanBackend = backendUrl.trimEnd('/')
                if (cleanBackend.isEmpty()) {
                    _pingState.value = PingState.Failed("Backend URL is required")
                    return@launch
                }
                // Local SSRF check (defensive — the backend also checks).
                val safe = SsrfGuard.validate(cleanBackend)
                if (safe == null) {
                    _pingState.value = PingState.Failed("Backend URL rejected (private/internal)")
                    return@launch
                }
                val config = ProviderConfig(
                    litellmUrl = litellmUrl.ifBlank { null },
                    litellmApiKey = litellmApiKey.ifBlank { null },
                    litellmModel = litellmModel.ifBlank { null },
                    geminiApiKey = geminiApiKey.ifBlank { null },
                    geminiModel = geminiModel.ifBlank { "gemini-3.7-flash" },
                )
                val body = json.encodeToString(
                    kotlinx.serialization.serializer<com.adnanfoisal.infinitydesign.generation.blueprint.PingRequest>(),
                    com.adnanfoisal.infinitydesign.generation.blueprint.PingRequest(kind, config),
                )
                val req = Request.Builder()
                    .url("$safe/api/ping")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = http.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (resp.code == 200) {
                    val parsed = json.decodeFromString(PingResponse.serializer(), respBody)
                    if (parsed.ok) {
                        _pingState.value = PingState.Ok(parsed.provider, parsed.model, parsed.durationMs)
                    } else {
                        _pingState.value = PingState.Failed(parsed.message.ifBlank { "Provider reported failure" })
                    }
                } else {
                    _pingState.value = PingState.Failed("HTTP ${resp.code}: ${respBody.take(200)}")
                }
            } catch (_: CancellationException) {
                _pingState.value = PingState.Idle
            } catch (e: Throwable) {
                _pingState.value = PingState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelPing() { pingJob?.cancel(); _pingState.value = PingState.Idle }
}
