package com.hopcape.odo.feature.advisory.domain.checklist

/**
 * What this service should cover, and what it should not.
 *
 * Two lists rather than one flagged list, because the screen is two sections and an owner
 * reads them differently: the first is what to insist on, the second is what to refuse.
 */
internal data class PreServiceChecklist(
    val due: List<ChecklistItem>,
    val notYet: List<ChecklistItem>,
) {
    /**
     * The schedule said nothing.
     *
     * Counts only what the schedule produced. [notYet] always carries the counter upsells,
     * which exist whether or not a schedule was read, so testing it whole would make this
     * permanently false.
     */
    val isEmpty: Boolean
        get() = due.isEmpty() && notYet.none { it.label is ItemLabel.FromSchedule }
}

/**
 * One job on the list.
 *
 * No copy — [reason] carries the numbers and the screen writes the sentence. A job with the
 * same reason has to read the same way on every surface, and a string built here could not be
 * reached from a preview.
 */
internal data class ChecklistItem(
    /** The price tables' category slug, so the band lookup can key on it. */
    val slug: String,
    val label: ItemLabel,
    val reason: ChecklistReason,
    /** Services logged since this job was last done. 0 when the record never shows it. */
    val servicesSince: Int = 0,
)

/** Where the job's name comes from. */
internal sealed interface ItemLabel {

    /** The schedule row's own `display_name`. Reference data, not app copy. */
    data class FromSchedule(val name: String) : ItemLabel

    /** A job Odo names itself, because no schedule row does. The screen holds the string. */
    data class Upsell(val upsell: CounterUpsell) : ItemLabel
}

/**
 * Why a job is on the list it is on.
 *
 * The distance and the time cases are kept apart rather than folded into one "due" — an owner
 * arguing at a counter needs the fact, and "3 years" and "11,000 km" are different facts.
 */
internal sealed interface ChecklistReason {

    /** Due, and the record shows the car has driven this far since it was last done. */
    data class LastDoneKmAgo(val km: Int) : ChecklistReason

    /** Due, and the record shows this many months since it was last done. */
    data class LastDoneMonthsAgo(val months: Int) : ChecklistReason

    /** Due, and nothing in the record shows it was ever done. */
    data object NeverRecorded : ChecklistReason

    /** Not due: this far still to run. */
    data class KmToGo(val km: Int) : ChecklistReason

    /** Not due: this many months still to run. */
    data class MonthsToGo(val months: Int) : ChecklistReason

    /** The maker's schedule does not list this job at all. */
    data object NotInSchedule : ChecklistReason
}

/**
 * Jobs a workshop proposes that no service schedule asks for.
 *
 * A hand-written list, like [BillLineMatcher][com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher]'s
 * rules: these are the four things sold across a counter in India, and naming them is the
 * point of the section. Slugs match the price tables' vocabulary, so a band can still be
 * found for one if the tables ever carry it.
 */
internal enum class CounterUpsell(val slug: String) {
    INJECTOR_CLEANING("injector_cleaning"),
    ENGINE_FLUSH("engine_flush"),
    AC_DISINFECTANT("ac_disinfectant"),
    UNDERBODY_COATING("underbody_coating"),
}
