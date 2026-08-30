package com.adnanfoisal.infinitydesign.core.result

/**
 * Closed-form operation result. We avoid throwing across module boundaries.
 * Section 73 of the spec mandates: every recoverable error needs context.
 */
sealed class AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>()
    data class Err(val error: AppError) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Ok -> transform(value)
        is Err -> this
    }

    inline fun onError(block: (AppError) -> Unit): AppResult<T> {
        if (this is Err) block(error)
        return this
    }

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err
    fun getOrNull(): T? = (this as? Ok)?.value
    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Err -> throw IllegalStateException("${error.kind}: ${error.message}", error.cause)
    }
    fun getOrElse(default: @UnsafeVariance T): T = when (this) {
        is Ok -> value
        is Err -> default
    }
    fun getOrElseCatching(block: (AppError) -> @UnsafeVariance T): T = when (this) {
        is Ok -> value
        is Err -> block(error)
    }
}

inline fun <T> okResult(value: T): AppResult<T> = AppResult.Ok(value)
inline fun errResult(kind: AppError.Kind, message: String, cause: Throwable? = null): AppResult<Nothing> =
    AppResult.Err(AppError(kind, message, cause))

/** Convenience overload so callers can propagate an existing [AppError] verbatim. */
inline fun errResult(error: AppError): AppResult<Nothing> = AppResult.Err(error)

data class AppError(
    val kind: Kind,
    val message: String,
    val cause: Throwable? = null,
) {
    enum class Kind {
        NetworkUnreachable,
        NetworkTimeout,
        ProviderUnavailable,
        ProviderRefusal,
        MalformedResponse,
        EmptyResponse,
        RateLimited,
        Unauthorized,
        Forbidden,
        UnknownHttp,
        Cancelled,
        SchemaValidation,
        SchemaMigration,
        CorruptProject,
        StorageUnavailable,
        StoragePermission,
        NotFound,
        InvalidColor,
        InvalidNumber,
        InvalidCoordinate,
        InvalidDimension,
        InvalidElement,
        InvalidComposition,
        InvalidFont,
        InvalidPath,
        RendererFailure,
        LockedElement,
        InvalidOperation,
        OutOfMemory,
        Unknown,
    }
}
