package com.adnanfoisal.infinitydesign.generation.providers

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintResponse

/**
 * LLM Provider abstraction. Section 36: do not bake one provider throughout the app.
 *
 * Every implementation MUST validate input, enforce timeouts, and surface structured
 * errors. Section 38: never expose server API keys to clients; never log user keys.
 */
interface LlmProvider {
    val name: String
    suspend fun generateBlueprint(request: BlueprintRequest): AppResult<BlueprintResponse>

    /**
     * Lightweight request used by the "Test / Ping" button in the Android UI.
     * Should send the smallest possible request and report whether the credentials
     * are working. MUST NOT log secrets.
     */
    suspend fun ping(): AppResult<Long>

    /** Returns true if this provider is configured (has credentials set). */
    fun isConfigured(): Boolean
}

/** Common error builder for HTTP-status → AppError conversion. */
fun httpError(status: Int, body: String): AppError {
    val kind = when (status) {
        401, 403 -> AppError.Kind.Unauthorized
        429 -> AppError.Kind.RateLimited
        408 -> AppError.Kind.NetworkTimeout
        in 400..499 -> AppError.Kind.MalformedResponse
        in 500..599 -> AppError.Kind.ProviderUnavailable
        else -> AppError.Kind.UnknownHttp
    }
    val msg = "HTTP $status: ${body.take(300)}"
    return AppError(kind, msg)
}
