package com.hopcape.odo.infrastructure.firebase.analytics

/**
 * Always null. `FIRAnalytics.appInstanceIDWithCompletion` sits behind gitlive's own SwiftPM
 * cinterop package, which consumers cannot import, and the MVP is Android-only (CLAUDE.md).
 * The device row is still written on iOS — the column is nullable for exactly this.
 */
internal actual suspend fun readAppInstanceId(): String? = null
