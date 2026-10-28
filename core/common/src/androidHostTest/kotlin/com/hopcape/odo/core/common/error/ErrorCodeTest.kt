package com.hopcape.odo.core.common.error

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCodeTest {

    @Test
    fun hostLookupFailureIsDns() = assertEquals("dns", errorCode(UnknownHostException("x")))

    @Test
    fun socketTimeoutIsTimeout() = assertEquals("timeout", errorCode(SocketTimeoutException()))

    @Test
    fun refusedConnectionIsConnect() = assertEquals("connect", errorCode(ConnectException()))

    @Test
    fun handshakeFailureIsTls() = assertEquals("tls", errorCode(SSLHandshakeException("x")))

    @Test
    fun otherIoIsIo() = assertEquals("io", errorCode(IOException()))

    @Test
    fun cancellationIsCancelled() = assertEquals("cancelled", errorCode(CancellationException()))

    @Test
    fun unknownExceptionIsUnexpected() = assertEquals("unexpected", errorCode(RuntimeException()))
}
