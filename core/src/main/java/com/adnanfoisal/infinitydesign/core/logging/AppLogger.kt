package com.adnanfoisal.infinitydesign.core.logging

/**
 * Small SLF4J-like interface so that callers don't depend on android.util.Log or
 * java.util.logging. Each module supplies its own implementation.
 *
 * Section 74 of the spec mandates: never log API keys, tokens, or auth headers.
 */
interface AppLogger {
    fun verbose(tag: String, message: () -> String)
    fun debug(tag: String, message: () -> String)
    fun info(tag: String, message: () -> String)
    fun warn(tag: String, message: () -> String, throwable: Throwable? = null)
    fun error(tag: String, message: () -> String, throwable: Throwable? = null)

    companion object {
        /**
         * Secret scrubbing. Applied to every message before it leaves the logger.
         * Catches common forms: Bearer tokens, GitHub PATs, OpenAI/Gemini keys.
         */
        fun scrub(input: String): String {
            var out = input
            out = Regex("""(Bearer\s+)([A-Za-z0-9\-\._~+\/=]{8,})""", RegexOption.IGNORE_CASE).replace(out) { "${it.groupValues[1]}***REDACTED***" }
            out = Regex("""(sk-[A-Za-z0-9]{20,})""").replace(out) { "***REDACTED-sk***" }
            out = Regex("""(ghp_[A-Za-z0-9]{30,})""").replace(out) { "***REDACTED-pat***" }
            out = Regex("""(gho_[A-Za-z0-9]{30,})""").replace(out) { "***REDACTED-gho***" }
            out = Regex("""(AIza[A-Za-z0-9_\-]{35,})""").replace(out) { "***REDACTED-gemini***" }
            out = Regex("""(api[_-]?key\s*[:=]\s*"?)([^\s"\,]{16,})""", RegexOption.IGNORE_CASE).replace(out) { "${it.groupValues[1]}***REDACTED***" }
            return out
        }
    }
}

/** No-op logger for tests and pure-JVM modules. */
object NoopLogger : AppLogger {
    override fun verbose(tag: String, message: () -> String) {}
    override fun debug(tag: String, message: () -> String) {}
    override fun info(tag: String, message: () -> String) {}
    override fun warn(tag: String, message: () -> String, throwable: Throwable?) {}
    override fun error(tag: String, message: () -> String, throwable: Throwable?) {}
}

/** Default pure-JVM logger that prints to stdout. Fine for backend and tests. */
class StdoutLogger(private val minLevel: Level = Level.INFO) : AppLogger {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }
    private fun shouldEmit(level: Level) = level.ordinal >= minLevel.ordinal
    override fun verbose(tag: String, message: () -> String) {
        if (shouldEmit(Level.VERBOSE)) println("[V/$tag] ${AppLogger.scrub(message())}")
    }
    override fun debug(tag: String, message: () -> String) {
        if (shouldEmit(Level.DEBUG)) println("[D/$tag] ${AppLogger.scrub(message())}")
    }
    override fun info(tag: String, message: () -> String) {
        if (shouldEmit(Level.INFO)) println("[I/$tag] ${AppLogger.scrub(message())}")
    }
    override fun warn(tag: String, message: () -> String, throwable: Throwable?) {
        if (shouldEmit(Level.WARN)) {
            println("[W/$tag] ${AppLogger.scrub(message())}")
            throwable?.printStackTrace()
        }
    }
    override fun error(tag: String, message: () -> String, throwable: Throwable?) {
        if (shouldEmit(Level.ERROR)) {
            println("[E/$tag] ${AppLogger.scrub(message())}")
            throwable?.printStackTrace()
        }
    }
}
