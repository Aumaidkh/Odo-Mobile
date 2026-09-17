package com.hopcape.odo.infrastructure.firebase.analytics

import com.hopcape.odo.core.common.runCatchingCancellable
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.analytics.android
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * `getAppInstanceId()` hands back a `Task`, and it is awaited by listener rather than by
 * `Task.await()` so this module does not have to take a dependency on
 * kotlinx-coroutines-play-services for one call.
 */
internal actual suspend fun readAppInstanceId(): String? =
    runCatchingCancellable {
        suspendCancellableCoroutine { continuation ->
            Firebase.analytics.android.appInstanceId
                .addOnCompleteListener { task ->
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }
        }
    }.getOrNull()
