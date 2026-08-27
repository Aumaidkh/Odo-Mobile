package com.hopcape.odo.feature.support.domain

import com.hopcape.logging.api.DiagnosticReference
import com.hopcape.logging.api.DiagnosticRequests
import com.hopcape.logging.api.LogUploadScheduler
import com.hopcape.odo.core.platform.app.InstallationId
import kotlin.time.Clock

/**
 * Ask for this device's logs to be sent, and answer with the code the owner should quote.
 *
 * **The code is created before the upload, not after it.** The upload waits for a network and
 * may run hours later, in a process that does not exist yet, while the owner is already
 * typing their support mail. So the reference is generated here, written to the outbox, and
 * handed back straight away; the upload pass reads it out of the outbox whenever it runs and
 * files every file under it.
 *
 * The scheduler request is a nudge, not the delivery. It runs as soon as there is a
 * connection, and if the process dies first the outbox row is still there for the next
 * periodic pass to find.
 */
internal class RequestDiagnosticsUseCase(
    private val installationId: InstallationId,
    private val requests: DiagnosticRequests,
    private val scheduler: LogUploadScheduler,
    private val clock: Clock,
) {

    /** Opens a request and returns its reference, e.g. `ODO-AB12-CD34`. */
    suspend operator fun invoke(): String {
        val nowMs = clock.now().toEpochMilliseconds()
        val reference = DiagnosticReference.create(installationId.value, nowMs)
        requests.open(reference, nowMs)
        scheduler.requestUploadNow()
        return reference
    }
}
