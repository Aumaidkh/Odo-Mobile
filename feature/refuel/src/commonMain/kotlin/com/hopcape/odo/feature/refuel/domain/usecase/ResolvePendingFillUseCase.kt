package com.hopcape.odo.feature.refuel.domain.usecase

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.refuel.PendingFillStore
import com.hopcape.odo.core.platform.notification.DetectedFillNotifier
import kotlin.time.Clock

/**
 * Closes a detection, everywhere it is showing.
 *
 * A detection lives in two places at once — a row in the store and a notification in the shade
 * — and answering it in one place has to end it in both. Without that the owner confirms a
 * fill from the sheet, the notification stays where it was, and tapping its Confirm writes the
 * same tank a second time.
 *
 * One use case rather than each caller remembering to do both, because there are four ways to
 * answer a detection (the notification, the confirm sheet, the pending list, and rejecting it)
 * and the one that forgets is the one that produces a duplicate.
 *
 * The notification id is the pending row's id by construction, so dismissing needs nothing the
 * caller does not already have.
 */
internal class ResolvePendingFillUseCase(
    private val pending: PendingFillStore,
    private val notifier: DetectedFillNotifier,
    private val clock: Clock,
) {
    /**
     * @return whether this call is the one that closed it. False means it was already
     *   answered — which is exactly what a second Confirm on a stale notification is, and the
     *   caller should write nothing.
     */
    suspend operator fun invoke(id: String): Boolean {
        val wasOpen = runCatchingCancellableSuspend {
            pending.open().any { it.id == id }
        }.getOrDefault(false)

        // Dismissed either way. A notification for a question that is already answered is one
        // the owner can still tap, and it is the tap that duplicates the fill.
        notifier.dismiss(id)
        if (!wasOpen) return false

        runCatchingCancellableSuspend { pending.resolve(id, clock.now()) }
        return true
    }

    /**
     * The same, for a detection identified by what it detected rather than by its id.
     *
     * The confirm surface has only the draft: the id lives on the notification and on the row,
     * and threading it through every capture channel would put a field on the shared draft type
     * that one channel sets and the rest ignore.
     */
    suspend fun byContents(merchant: String?, amountPaise: Long?): Boolean {
        if (merchant == null) return false
        val match = runCatchingCancellableSuspend {
            pending.open().firstOrNull { it.merchant == merchant && it.amount?.paise == amountPaise }
        }.getOrNull() ?: return false
        return invoke(match.id)
    }
}
