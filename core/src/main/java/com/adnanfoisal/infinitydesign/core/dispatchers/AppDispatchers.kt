package com.adnanfoisal.infinitydesign.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Thin abstraction over Kotlin Dispatchers so the app can swap dispatchers in tests
 * and so that no caller reaches for GlobalScope.
 */
data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val unconfined: CoroutineDispatcher = Dispatchers.Unconfined,
)

object DefaultAppDispatchers {
    fun create(): AppDispatchers = AppDispatchers()
}
