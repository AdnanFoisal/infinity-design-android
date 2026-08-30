package com.adnanfoisal.infinitydesign.backend

import com.adnanfoisal.infinitydesign.backend.api.ErrorResponse
import com.adnanfoisal.infinitydesign.backend.api.RequestValidator
import com.adnanfoisal.infinitydesign.backend.api.toDesignBlueprint
import com.adnanfoisal.infinitydesign.backend.api.toHttpStatus
import com.adnanfoisal.infinitydesign.backend.providers.GeminiProvider
import com.adnanfoisal.infinitydesign.backend.providers.LiteLlmProvider
import com.adnanfoisal.infinitydesign.core.logging.AppLogger
import com.adnanfoisal.infinitydesign.core.logging.StdoutLogger
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.PingRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.PingResponse
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import com.adnanfoisal.infinitydesign.generation.providers.LlmProvider
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.netty.EngineMain
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun Application.module(logger: AppLogger = StdoutLogger()) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = false
        })
    }
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(RateLimit) {
        global {
            rateLimiter(60, 60.seconds)
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val msg = cause.message ?: cause.javaClass.simpleName
            logger.error("backend", { "unhandled: $msg" }, cause)
            call.respond(HttpStatusCode.InternalServerError,
                ErrorResponse("internal error", "Unknown", cause.message))
        }
    }

    routing {
        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }

        route("/api") {
            post("/ping") {
                val req = call.receive<PingRequest>()
                val provider = providerFor(req.provider, req.providerConfig)
                if (!provider.isConfigured()) {
                    val v = RequestValidator.validateProviderConfigForPing(req.providerConfig, req.provider)
                    val msg = (v as? AppResult.Err)?.error?.message ?: "Provider not configured"
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("not configured", "Unauthorized", msg))
                    return@post
                }
                when (val r = provider.ping()) {
                    is AppResult.Ok -> call.respond(PingResponse(
                        ok = true,
                        provider = provider.name,
                        model = req.providerConfig.geminiModel.ifBlank { req.providerConfig.litellmModel ?: "unknown" },
                        durationMs = r.value,
                        message = "OK",
                    ))
                    is AppResult.Err -> call.respond(r.error.kind.toHttpStatus(),
                        ErrorResponse(r.error.message, r.error.kind.name, r.error.cause?.message))
                }
            }

            post("/blueprint/generate") {
                val req = call.receive<BlueprintRequest>()
                val v = RequestValidator.validateBlueprintRequest(req)
                if (v is AppResult.Err) {
                    call.respond(v.error.kind.toHttpStatus(),
                        ErrorResponse(v.error.message, v.error.kind.name, null))
                    return@post
                }
                val validReq = v.getOrThrow()
                val provider = providerFor(validReq.provider, validReq.providerConfig)
                if (!provider.isConfigured()) {
                    call.respond(HttpStatusCode.Unauthorized,
                        ErrorResponse("Provider not configured", "Unauthorized", null))
                    return@post
                }
                when (val r = provider.generateBlueprint(validReq)) {
                    is AppResult.Ok -> {
                        val bp = r.value.blueprint.toDesignBlueprint(r.value.prompt)
                        val vr = com.adnanfoisal.infinitydesign.design.validation.DesignValidator.validateBlueprint(bp)
                        if (vr is AppResult.Err) {
                            call.respond(HttpStatusCode.BadGateway,
                                ErrorResponse("LLM produced invalid blueprint", "MalformedResponse", vr.error.message))
                            return@post
                        }
                        call.respond(r.value)
                    }
                    is AppResult.Err -> call.respond(r.error.kind.toHttpStatus(),
                        ErrorResponse(r.error.message, r.error.kind.name, r.error.cause?.message))
                }
            }
        }
    }
}

fun providerFor(kind: ProviderKind, config: ProviderConfig): LlmProvider = when (kind) {
    ProviderKind.GEMINI -> GeminiProvider(config)
    ProviderKind.LITELLM -> LiteLlmProvider(config)
}

fun main(args: Array<String>) {
    EngineMain.main(args)
}
