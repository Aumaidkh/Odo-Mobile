package com.hopcape.odo.core.domain.cost.model

/**
 * How a fill reached the app.
 *
 * Every capture channel builds the same [FuelFillDraft] and ends at the same confirm step,
 * so this is the only thing that separates them afterwards. It is stored on the row because
 * two questions need it later: which channels owners actually use, and how much of the
 * record was typed by a person rather than read by a machine.
 *
 * It is not a claim about accuracy. A detected fill still passes through a confirm step
 * where the owner can change every number, so a `DETECTED` row is as owner-checked as a
 * `MANUAL` one unless silent logging is on.
 */
enum class FillEntrySource {

    /** Read from a payment notification, then confirmed. */
    DETECTED,

    /** Read off the pump's own display by the camera. */
    PUMP_OCR,

    /** Started from the last visit's station, rate and predicted odometer. */
    PREFILLED,

    /** Typed in with nothing to start from. */
    MANUAL,
}
