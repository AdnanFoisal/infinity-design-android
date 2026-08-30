package com.adnanfoisal.infinitydesign.backend.providers

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintResponse
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.prompts.BlueprintPrompts
import com.adnanfoisal.infinitydesign.generation.providers.LlmProvider
import com.adnanfoisal.infinitydesign.generation.providers.SsrfGuard
import com.adnanfoisal.infinitydesign.generation.providers.httpError



import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LiteLLM provider — the user supplies their own LiteLLM proxy URL and key.
 *
 * Section 39/40: BYOK + SSRF protection. The URL is validated by [SsrfGuard]
 * before any request goes out. The key is forwarded as a Bearer token to the
 * user-supplied URL only — never logged, never stored.
 *
 * Tested via the /ping endpoint — the user can verify their setup before
 * committing to a full blueprint generation.
 */
class LiteLlmProvider(
    private val config: ProviderConfig,
    private val client: HttpClient = defaultClient(),
) : LlmProvider {

    override val name: String = "litellm"

    override fun isConfigured(): Boolean {
        val url = config.litellmUrl
        return !url.isNullOrBlank() &&
                !config.litellmApiKey.isNullOrBlank() &&
                !config.litellmModel.isNullOrBlank() &&
                SsrfGuard.validate(url) != null
    }

    override suspend fun ping(): AppResult<Long> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext errResult(AppError.Kind.Unauthorized, "LiteLLM not configured")
        }
        val url = SsrfGuard.validate(config.litellmUrl!!)
            ?: return@withContext errResult(AppError.Kind.Forbidden, "LiteLLM URL rejected by SSRF guard")
        val start = System.currentTimeMillis()
        val body = OpenAiRequest(
            model = config.litellmModel!!,
            messages = listOf(
                OpenAiMessage(role = "system", content = "You are a ping responder. Reply with the single word 'pong'."),
                OpenAiMessage(role = "user", content = "ping"),
            ),
            maxTokens = 4,
            temperature = 0.0,
        )
        try {
            val resp = client.post("$url/v1/chat/completions") {
                bearerAuth(config.litellmApiKey!!)
                contentType(ContentType.Application.Json)
                setBody(body)
                timeout { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
            }
            val elapsed = System.currentTimeMillis() - start
            if (!resp.status.isSuccess()) {
                val text = resp.body<String>()
                return@withContext errResult(httpError(resp.status.value, text).kind, "LiteLLM ping failed", null)
            }
            okResult(elapsed)
        } catch (e: Throwable) {
            errResult(AppError.Kind.NetworkTimeout, "LiteLLM ping exception: ${e.message}", e)
        }
    }

    override suspend fun generateBlueprint(request: BlueprintRequest): AppResult<BlueprintResponse> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext errResult(AppError.Kind.Unauthorized, "LiteLLM not configured")
            }
            val url = SsrfGuard.validate(config.litellmUrl!!)
                ?: return@withContext errResult(AppError.Kind.Forbidden, "LiteLLM URL rejected")
            val start = System.currentTimeMillis()
            val sys = BlueprintPrompts.system
            val userText = BlueprintPrompts.userPrompt(
                BlueprintPrompts.PromptInputs(
                    prompt = request.prompt,
                    style = request.style,
                    aspectId = request.aspectId,
                    seed = request.seed,
                )
            )
            val reqBody = OpenAiRequest(
                model = config.litellmModel!!,
                messages = listOf(
                    OpenAiMessage(role = "system", content = sys),
                    OpenAiMessage(role = "user", content = userText),
                ),
                maxTokens = 4096,
                temperature = 0.85,
            )
            try {
                val resp = client.post("$url/v1/chat/completions") {
                    bearerAuth(config.litellmApiKey!!)
                    contentType(ContentType.Application.Json)
                    setBody(reqBody)
                    timeout { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 55_000 }
                }
                if (!resp.status.isSuccess()) {
                    val text = resp.body<String>()
                    return@withContext errResult(httpError(resp.status.value, text).kind, "LiteLLM blueprint failed", null)
                }
                val raw: OpenAiResponse = resp.body()
                val elapsed = System.currentTimeMillis() - start
                val text = raw.choices.firstOrNull()?.message?.content
                    ?: return@withContext errResult(AppError.Kind.EmptyResponse, "LiteLLM returned no text")
                parseBlueprint(text, request.prompt, elapsed)
            } catch (e: Throwable) {
                errResult(AppError.Kind.NetworkTimeout, "LiteLLM blueprint exception: ${e.message}", e)
            }
        }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false; prettyPrint = false }) }
        }
    }
}

@Serializable
data class OpenAiRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<OpenAiMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    @SerialName("temperature") val temperature: Double = 0.85,
)

@Serializable
data class OpenAiMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
)

@Serializable
data class OpenAiResponse(
    @SerialName("choices") val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
data class OpenAiChoice(
    @SerialName("message") val message: OpenAiMessage,
)
