package com.hopcape.odo.infrastructure.supabase.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/** Darwin — NSURLSession, so iOS system proxy and certificate settings apply. */
internal actual fun supabaseHttpClientEngine(): HttpClientEngine = Darwin.create()
