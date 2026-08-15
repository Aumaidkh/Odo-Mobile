package com.hopcape.odo.core.domain.record.entitlement

/**
 * How many record PDFs the owner has exported.
 *
 * The tally half of the record-export cap: the plan says how many are permitted, this says how
 * many are gone. They are kept apart for the reason
 * [Quota][com.hopcape.odo.core.domain.entitlement.Quota] documents — the plan cannot know the
 * count, and the count cannot know the plan.
 *
 * Unlike a detected fill, an export leaves nothing behind. The PDF goes to the share sheet and
 * the app never sees it again, so there is no row to count afterwards and the act has to be
 * recorded as it happens.
 *
 * The count is a lifetime one. Which period that is, is the implementation's to know — the
 * caller has no business deciding when a cap resets, and every caller deciding separately is
 * how two screens end up disagreeing about it. The same reasoning as
 * [ScanUsage][com.hopcape.odo.core.domain.scan.entitlement.ScanUsage].
 */
interface RecordExportUsage {

    /** Exports spent so far. Zero before the first one. */
    suspend fun used(): Int

    /**
     * Count one export.
     *
     * Called once the PDF exists and has been handed to the owner. A render that failed is not
     * charged: it costs nothing to run and gave them nothing, and charging for it would make a
     * broken export cost one of three.
     */
    suspend fun recordExport()
}
