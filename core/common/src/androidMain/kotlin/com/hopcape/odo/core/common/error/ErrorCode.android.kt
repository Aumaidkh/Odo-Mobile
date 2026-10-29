package com.hopcape.odo.core.common.error

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/** Library names are checked first and up the superclass chain; the R8 keep rules keep them. */
actual fun errorCode(throwable: Throwable): String {
    namedCode(throwable)?.let { return it }
    return when (throwable) {
        is CancellationException -> ErrorCode.CANCELLED
        is UnknownHostException -> ErrorCode.DNS
        is InterruptedIOException -> ErrorCode.TIMEOUT
        is SSLException -> ErrorCode.TLS
        is ConnectException -> ErrorCode.CONNECT
        is IOException -> ErrorCode.IO
        else -> ErrorCode.UNEXPECTED
    }
}

private fun namedCode(throwable: Throwable): String? {
    var type: Class<*>? = throwable.javaClass
    while (type != null) {
        namedErrorCodes[type.name]?.let { return it }
        type = type.superclass
    }
    return null
}
