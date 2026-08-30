package com.adnanfoisal.infinitydesign.backend.providers

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintDensity
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintPalette
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintTypography
import com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem
import com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintResponse
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintPayload
import com.adnanfoisal.infinitydesign.generation.blueprint.PalettePayload
import com.adnanfoisal.infinitydesign.generation.blueprint.PingResponse
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import com.adnanfoisal.infinitydesign.generation.blueprint.TypographyPayload
import com.adnanfoisal.infinitydesign.generation.prompts.BlueprintPrompts
import com.adnanfoisal.infinitydesign.generation.providers.LlmProvider
import com.adnanfoisal.infinitydesign.generation.providers.httpError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Google Gemini provider. Supports BYOK — the Android client sends the user's
 * Gemini API key (never logged) and the backend proxies to Google's endpoints.
 *
 * Section 39: the backend does NOT store the key. It only forwards it to Google.
 * The user must be told this clearly.
 *
 * Verified model: gemini-3.7-flash (latest as of Aug 2026).
 */
class GeminiProvider(
    private val config: ProviderConfig,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val client: HttpClient = defaultClient(),
) : LlmProvider {

    override val name: String = "gemini"

    override fun isConfigured(): Boolean = !config.geminiApiKey.isNullOrBlank()

    override suspend fun ping(): AppResult<Long> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext errResult(AppError.Kind.Unauthorized, "Gemini API key not set")
        }
        val model = config.geminiModel.ifBlank { "gemini-3.7-flash" }
        val start = System.currentTimeMillis()
        val body = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = "ping")),
                ),
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.0,
                maxOutputTokens = 4,
                topP = 1.0,
                topK = 1,
            ),
        )
        try {
            val response = client.post("$baseUrl/v1beta/models/${model}:generateContent?key=${sanitiseKey(config.geminiApiKey)}") {
                contentType(ContentType.Application.Json)
                setBody(body)
                timeout { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
            }
            val elapsed = System.currentTimeMillis() - start
            if (!response.status.isSuccess()) {
                val text = response.body<String>()
                return@withContext errResult(httpError(response.status.value, text).kind,
                    "Gemini ping failed: HTTP ${response.status.value}", null)
            }
            okResult(elapsed)
        } catch (e: Throwable) {
            errResult(AppError.Kind.NetworkTimeout, "Gemini ping exception: ${e.message}", e)
        }
    }

    override suspend fun generateBlueprint(request: BlueprintRequest): AppResult<BlueprintResponse> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext errResult(AppError.Kind.Unauthorized, "Gemini API key not set")
            }
            val model = config.geminiModel.ifBlank { "gemini-3.7-flash" }
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
            val reqBody = GeminiRequest(
                systemInstruction = GeminiContent(role = "user", parts = listOf(GeminiPart(text = sys))),
                contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userText)))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.85,
                    maxOutputTokens = 4096,
                    topP = 0.95,
                    topK = 40,
                ),
            )
            try {
                val resp = client.post("$baseUrl/v1beta/models/${model}:generateContent?key=${sanitiseKey(config.geminiApiKey)}") {
                    contentType(ContentType.Application.Json)
                    setBody(reqBody)
                    timeout { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 55_000 }
                }
                if (!resp.status.isSuccess()) {
                    val text = resp.body<String>()
                    return@withContext errResult(httpError(resp.status.value, text).kind, "Gemini blueprint failed", null)
                }
                val raw: GeminiResponse = resp.body()
                val elapsed = System.currentTimeMillis() - start
                val text = raw.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: return@withContext errResult(AppError.Kind.EmptyResponse, "Gemini returned no text")
                val parsed = parseBlueprint(text, request.prompt, elapsed)
                parsed
            } catch (e: Throwable) {
                errResult(AppError.Kind.NetworkTimeout, "Gemini blueprint exception: ${e.message}", e)
            }
        }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false; prettyPrint = false }) }
        }

        /** Replace any non-ASCII characters — keys should be clean ASCII. */
        fun sanitiseKey(k: String?): String = k?.filter { it.isLetterOrDigit() || it == '-' || it == '_' } ?: ""
    }
}

@Serializable
data class GeminiRequest(
    @SerialName("contents") val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent? = null,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    @SerialName("role") val role: String,
    @SerialName("parts") val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    @SerialName("text") val text: String? = null,
)

@Serializable
data class GeminiGenerationConfig(
    @SerialName("temperature") val temperature: Double = 0.85,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int = 4096,
    @SerialName("topP") val topP: Double = 0.95,
    @SerialName("topK") val topK: Int = 40,
)

@Serializable
data class GeminiResponse(
    @SerialName("candidates") val candidates: List<GeminiCandidate>? = null,
)

@Serializable
data class GeminiCandidate(
    @SerialName("content") val content: GeminiContent? = null,
)

/**
 * Parse the LLM's JSON output into a [BlueprintResponse].
 *
 * Section 41: malformed/empty responses must be handled gracefully — no crash.
 * Section 60: required content must be preserved.
 */
internal fun parseBlueprint(raw: String, originalPrompt: String, elapsedMs: Long): AppResult<BlueprintResponse> {
    val cleaned = extractJson(raw)
        ?: return errResult(AppError.Kind.MalformedResponse, "No JSON in response")
    return try {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
        val obj = json.parseToJsonElement(cleaned) as? JsonObject
            ?: return errResult(AppError.Kind.MalformedResponse, "Not a JSON object")
        val palette = obj["palette"]?.jsonObject
            ?: return errResult(AppError.Kind.SchemaValidation, "Missing palette")
        val typography = obj["typography"]?.jsonObject
            ?: return errResult(AppError.Kind.SchemaValidation, "Missing typography")
        val bp = BlueprintPayload(
            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled",
            purpose = obj["purpose"]?.jsonPrimitive?.contentOrNull ?: "",
            audience = obj["audience"]?.jsonPrimitive?.contentOrNull ?: "",
            mood = obj["mood"]?.jsonPrimitive?.contentOrNull ?: "",
            visualDirection = obj["visualDirection"]?.jsonPrimitive?.contentOrNull ?: "",
            palette = PalettePayload(
                name = palette["name"]?.jsonPrimitive?.contentOrNull ?: "Custom",
                primary = palette["primary"]?.jsonPrimitive?.contentOrNull ?: "#000000",
                secondary = palette["secondary"]?.jsonPrimitive?.contentOrNull ?: "#FFFFFF",
                accent = palette["accent"]?.jsonPrimitive?.contentOrNull ?: "#3F51B5",
                neutrals = palette["neutrals"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
                background = palette["background"]?.jsonPrimitive?.contentOrNull ?: "#FFFFFF",
                foreground = palette["foreground"]?.jsonPrimitive?.contentOrNull ?: "#000000",
            ),
            typography = TypographyPayload(
                displayRole = typography["displayRole"]?.jsonPrimitive?.contentOrNull ?: "neutral-sans",
                bodyRole = typography["bodyRole"]?.jsonPrimitive?.contentOrNull ?: "neutral-sans",
                captionRole = typography["captionRole"]?.jsonPrimitive?.contentOrNull ?: "neutral-sans",
                displayWeight = typography["displayWeight"]?.jsonPrimitive?.intOrNull ?: 700,
                bodyWeight = typography["bodyWeight"]?.jsonPrimitive?.intOrNull ?: 400,
                displayTracking = (typography["displayTracking"]?.jsonPrimitive?.contentOrNull ?: "0").toFloatOrNull() ?: 0f,
                bodyTracking = (typography["bodyTracking"]?.jsonPrimitive?.contentOrNull ?: "0").toFloatOrNull() ?: 0f,
            ),
            composition = obj["composition"]?.jsonPrimitive?.contentOrNull ?: "editorial",
            visualLanguage = obj["visualLanguage"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
            density = BlueprintDensity.valueOf(obj["density"]?.jsonPrimitive?.contentOrNull ?: "BALANCED"),
            texture = obj["texture"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
            decorative = obj["decorative"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
            lighting = obj["lighting"]?.jsonPrimitive?.contentOrNull ?: "",
            hierarchy = obj["hierarchy"]?.jsonArray?.mapNotNull {
                val ho = it.jsonObject
                HierarchyItem(
                    role = ho["role"]?.jsonPrimitive?.contentOrNull ?: "",
                    label = ho["label"]?.jsonPrimitive?.contentOrNull ?: "",
                    importance = ho["importance"]?.jsonPrimitive?.intOrNull ?: 5,
                )
            } ?: emptyList(),
            semanticContent = obj["semanticContent"]?.jsonArray?.mapNotNull {
                val sc = it.jsonObject
                SemanticContentItem(
                    role = sc["role"]?.jsonPrimitive?.contentOrNull ?: "",
                    content = sc["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    protected = sc["protected"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                )
            } ?: emptyList(),
            imagery = obj["imagery"]?.jsonPrimitive?.contentOrNull ?: "",
            constraints = obj["constraints"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
            seed = obj["seed"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        )
        okResult(BlueprintResponse(bp, originalPrompt, elapsedMs))
    } catch (e: Throwable) {
        errResult(AppError.Kind.MalformedResponse, "Failed to parse blueprint: ${e.message}", e)
    }
}

/**
 * Extract the largest balanced JSON object from a raw LLM output.
 * Handles markdown fences (```json ... ```) and preamble prose.
 */
internal fun extractJson(raw: String): String? {
    val s = raw.trim()
    if (s.startsWith("{") && s.endsWith("}")) return s
    val fence = Regex("""```(?:json)?\s*(\{[\s\S]*\})\s*```""").find(s)
    if (fence != null) return fence.groupValues[1]
    val start = s.indexOf('{')
    val end = s.lastIndexOf('}')
    if (start >= 0 && end > start) return s.substring(start, end + 1)
    return null
}
