package com.hopcape.odo.core.common.error

/**
 * A stable analytics code for [throwable]: `dns`, `timeout`, `connect`, `tls`, `io`, `http`,
 * `decode`, `cancelled` or `unexpected`. Never a class name, which R8 shortens in release.
 */
expect fun errorCode(throwable: Throwable): String

/** The codes [errorCode] can return. */
object ErrorCode {
    const val DNS = "dns"
    const val TIMEOUT = "timeout"
    const val CONNECT = "connect"
    const val TLS = "tls"
    const val IO = "io"
    const val HTTP = "http"
    const val DECODE = "decode"
    const val CANCELLED = "cancelled"
    const val UNEXPECTED = "unexpected"
}

/** Library types matched by name, so this module needs no Ktor or serialization dependency. */
internal val namedErrorCodes: Map<String, String> = mapOf(
    "io.ktor.client.plugins.HttpRequestTimeoutException" to ErrorCode.TIMEOUT,
    "io.ktor.client.network.sockets.ConnectTimeoutException" to ErrorCode.TIMEOUT,
    "io.ktor.client.network.sockets.SocketTimeoutException" to ErrorCode.TIMEOUT,
    "io.ktor.client.plugins.ResponseException" to ErrorCode.HTTP,
    "kotlinx.serialization.SerializationException" to ErrorCode.DECODE,
)
