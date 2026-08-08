package com.hopcape.odo.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Same contract as [kotlin.runCatching], except a [CancellationException] is rethrown
 * instead of captured.
 *
 * `runCatching` catches `Throwable`, which also catches `CancellationException` — silently
 * swallowing a coroutine's cancellation instead of letting it propagate, and breaking
 * structured concurrency. Use this wherever the goal is to isolate a caller from a
 * failing call (a vendor SDK, IO) without also isolating it from being cancelled.
 */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
