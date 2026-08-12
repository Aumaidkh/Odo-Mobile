package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import kotlinx.coroutines.flow.first

/**
 * Attach a bill photo to an existing entry — the "Add a bill to verify" action behind a
 * self-reported card. This is what earns the entry its **Verified** badge.
 *
 * The picked file is **copied into app storage first**, and the entry stores the copy's key.
 * What the picker hands back is a borrowed handle that stops resolving once the process dies,
 * so storing it directly would give the owner proof that opens today and fails next month.
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
    private val files: PlatformFileStore,
) {
    /**
     * [pickedRef] is whatever the picker returned. The copy lands at
     * `bills/{carId}/{logId}.{ext}`, mirroring the `bills` storage bucket convention minus
     * the owner segment, so uploading it later is prefixing the key rather than working out
     * where the file should have gone.
     */
    suspend operator fun invoke(
        id: ServiceLogId,
        carId: CarId,
        pickedRef: String,
    ): Either<DomainError, ServiceLogEntry> {
        val entry = logs.observe(id).first() ?: return DomainError.ServiceLogNotFound.left()
        return files.save(pickedRef = pickedRef, directory = directoryFor(carId), fileName = id.value)
            .flatMap { storageKey -> logs.update(entry.withBillPhoto(storageKey)) }
    }

    private companion object {
        const val ROOT = "bills"

        fun directoryFor(carId: CarId): String = "$ROOT/${carId.value}"
    }
}
