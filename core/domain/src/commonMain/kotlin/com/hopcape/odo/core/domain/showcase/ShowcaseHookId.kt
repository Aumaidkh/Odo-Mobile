package com.hopcape.odo.core.domain.showcase

/**
 * Every coach mark the app can show — the whole budget, in one file (#224).
 *
 * An enum on purpose: the epic's premise is that the budget is small enough to matter,
 * and a stringly-typed id would let a seventh hook in without anyone noticing. Adding a
 * case here is the deliberate act the epic asks for.
 *
 * Each hook fires on its own surface, at the moment that surface first matters — never
 * as a first-run carousel. Which condition makes each one due is the owning feature's
 * decision; this only names them.
 */
enum class ShowcaseHookId {

    /** The bottom bar's camera button: a photo of a bill becomes a logged service and a price check (#228). */
    SCAN_BUTTON,

    /** Costs showing "not enough distance yet": updating the reading is what turns this into a number (#229). */
    ODOMETER_CURRENT,

    /** The fairness entry on the first logged bill: Odo can say whether this price was fair (#230). */
    FAIRNESS_CHECK,

    /** The vault: uploading a policy is setting a renewal alarm, not filing paper (#231). */
    DOCUMENT_REMINDERS,

    /** The health score on Home: the number responds to what gets logged — Pro-gated breakdown (#232). */
    HEALTH_SCORE_BREAKDOWN,

    /** The Timeline's share action: the record leaves as one PDF a buyer will read — Pro-gated (#233). */
    RECORD_EXPORT,

    /** The bell on Home: it is the only door into Reminders, and it carries no badge to say so. */
    REMINDERS_BELL,
}
