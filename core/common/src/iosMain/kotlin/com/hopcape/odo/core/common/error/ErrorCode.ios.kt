package com.hopcape.odo.core.common.error

import kotlin.coroutines.cancellation.CancellationException

/** Native has no superclass reflection, so only exact names and `IOException` are told apart. */
actual fun errorCode(throwable: Throwable): String {
    val name = throwable::class.qualifiedName.orEmpty()
    return when {
        throwable is CancellationException -> ErrorCode.CANCELLED
        name in namedErrorCodes -> namedErrorCodes.getValue(name)
        name.endsWith("IOException") -> ErrorCode.IO
        else -> ErrorCode.UNEXPECTED
    }
}
