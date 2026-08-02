package com.hopcape.odo.infrastructure.supabase.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** OkHttp — the engine Android already has connection pooling and HTTP/2 through. */
internal actual fun supabaseHttpClientEngine(): HttpClientEngine = OkHttp.create()
