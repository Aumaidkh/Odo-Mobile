package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first

/**
 * Attach a bill photo to an existing entry — the "Add a bill to verify" action behind a
 * self-reported card. This is what earns the entry its **Verified** badge.
 *
 * Deliberately a **local write and nothing else.** Attaching the photo also makes the entry
 * fairness-checkable, but benchmarking needs the city pool from the server, and an
 * offline-first app may not have it: binding the two would mean a slow or failed benchmark
 * could cost the owner the photo they just took. So the verdict is a separate step —
 * [RecordEntryFairnessUseCase] — run after this one succeeds, and free to fail or be
 * retried without touching the attachment.
 *
 * "Verified but not yet judged" is already a legal state (an owner with no city, or a
 * category with no benchmark, lands there anyway), so nothing new breaks by passing
 * through it.
 */
internal class AttachBillPhotoUseCase(
    private val logs: ServiceLogRepository,
) {
    suspend operator fun invoke(
        id: ServiceLogId,
        billPhotoRef: String,
    ): Either<DomainError, ServiceLogEntry> {
        val entry = logs.observe(id).first() ?: return DomainError.ServiceLogNotFound.left()
        return logs.update(entry.withBillPhoto(billPhotoRef))
    }
}
