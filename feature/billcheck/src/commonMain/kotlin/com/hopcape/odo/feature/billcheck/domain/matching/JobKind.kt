package com.hopcape.odo.feature.billcheck.domain.matching

/**
 * A job the price tables can be asked about, by the slug they key on.
 *
 * Not [ServiceCategory][com.hopcape.odo.core.domain.servicelog.model.ServiceCategory], which
 * has nine values and is the service log's tagging vocabulary. The price tables key on
 * `service_categories.slug` and are far finer: "Engine oil + filter" is two priced jobs there
 * — `engine_oil` and `oil_filter` — and neither has a `ServiceCategory` that can name it.
 *
 * **Whether a slug has a price is not recorded here.** Eight of the server's categories have
 * no `job_prices` row today and that changes as reference data is entered, so the band lookup
 * discovers it and the line becomes unchecked. A flag here would be stale within a week.
 */
internal enum class JobKind(val slug: String) {
    ENGINE_OIL("engine_oil"),
    OIL_FILTER("oil_filter"),
    OIL_CHANGE("oil_change"),
    AIR_FILTER("air_filter"),
    AC_SERVICE("ac_service"),
    COOLANT("coolant"),
    BRAKE_PADS("brake_pads"),
    BRAKE_DISC("brake_disc"),
    BRAKE_FLUID("brake_fluid"),
    BRAKES("brakes"),
    WHEEL_ALIGNMENT("wheel_alignment"),
    TYRE_ROTATION("tyre_rotation"),
    TYRES("tyres"),
    WIPERS("wipers"),
    BATTERY("battery"),
    CLUTCH("clutch"),
    SUSPENSION("suspension"),
    ELECTRICAL("electrical"),
    DENTING_PAINTING("denting_painting"),
    GENERAL_SERVICE("general_service"),
}

/** What a bill line turned out to be. */
internal sealed interface LineMatch {

    /** A job the tables can price. */
    data class Job(val kind: JobKind) : LineMatch

    /**
     * Not a job at all — labour, consumables, tax, a discount.
     *
     * Kept apart from [Unknown] because the two mean different things to the owner. A modelled
     * band already includes labour, so a separate labour line is not something Odo failed to
     * check; it is something there is nothing to check *against*.
     */
    data object NotAJob : LineMatch

    /** A job, probably, that the rules could not name. */
    data object Unknown : LineMatch
}
