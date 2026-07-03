package com.hopcape.odo.core.domain.servicelog.model

/**
 * What kind of work a service (or a line item) was — the "what was done" tags on an
 * entry and the key the fairness check compares against a city average. Mirrors common
 * Indian workshop job types; [OTHER] covers anything not listed (paired with the
 * free-text label/notes).
 */
enum class ServiceCategory {
    OIL_CHANGE,
    BRAKES,
    TYRES,
    AC,
    BATTERY,
    SUSPENSION,
    ELECTRICAL,
    GENERAL_SERVICE,
    OTHER,
}
