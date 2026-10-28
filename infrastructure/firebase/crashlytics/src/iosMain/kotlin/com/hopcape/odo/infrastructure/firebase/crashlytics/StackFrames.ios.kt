package com.hopcape.odo.infrastructure.firebase.crashlytics

/** No-op: Kotlin/Native exposes no settable stack trace, and iOS is Phase 2. */
internal actual fun Throwable.restoreFrames(formatted: String) = Unit
