package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hopcape.logging.api.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Asks for the notification listener to be bound again, on a schedule.
 *
 * This exists because of a state nothing else can get out of: the process dies, the grant is
 * still in place, and the system does not bind the listener again. Every other rebind path
 * needs code to be running — the service's own `onListenerDisconnected`, the app-start rebind
 * — and in that state none of it is. Detection is silently dead until the owner happens to
 * open Odo, which they have no reason to do, because the whole point of detection is that they
 * do not have to.
 *
 * A scheduled job is the only thing that runs without them. WorkManager starts the process to
 * execute it, and starting the process is most of the fix — the rebind request that follows is
 * the rest.
 *
 * **Force-stop is not recoverable and this does not pretend otherwise.** An app the owner
 * force-stopped, or that a task killer stopped, carries a flag that stops every scheduled job
 * until they open it themselves. That is Android's design, not a gap to work around.
 *
 * The dependencies come through [KoinComponent] because WorkManager constructs its own
 * workers; a custom `WorkerFactory` would be a second wiring path for one class.
 */
internal class ListenerRearmWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val access: NotificationAccess by inject()
    private val logger: Logger by inject()

    override suspend fun doWork(): Result {
        // `requestRebind` no-ops when access was never granted or has been revoked, so there
        // is nothing to check here first.
        access.requestRebind()
        logger.debug(TAG, "refuel_listener_rearm", fields = mapOf(GRANTED to access.isGranted()))
        // Always success. There is no failure to retry — either the grant is there and the
        // rebind was asked for, or it is not and the next run will find the same thing.
        return Result.success()
    }

    internal companion object {
        const val TAG = "refuel_listener_rearm"
        private const val GRANTED = "granted"
    }
}
