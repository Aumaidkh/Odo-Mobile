package com.hopcape.odo.core.platform.review

/** The moments Odo may ask for a store review, as the `trigger` analytics value. */
enum class ReviewTrigger(val value: String) {
    FIRST_BILL("first_bill"),
    FIRST_REMINDER("first_reminder"),
    TRIP_DAY_7("trip_day_7"),
}

/** Asks for a store review at a good moment, within [ReviewPromptPolicy]'s limits. Never throws. */
interface ReviewPrompter {

    /** A good moment happened. The prompter decides whether asking is allowed now. */
    suspend fun maybeAsk(trigger: ReviewTrigger)

    /** Asks with [ReviewTrigger.TRIP_DAY_7] once the active car has trips on 7 distinct days. */
    suspend fun maybeAskForTripDays()

    /** A paywall was shown, so this session never asks. */
    fun paywallShown()
}

/** For graphs with no store review, such as iOS and tests. */
object NoopReviewPrompter : ReviewPrompter {
    override suspend fun maybeAsk(trigger: ReviewTrigger) = Unit
    override suspend fun maybeAskForTripDays() = Unit
    override fun paywallShown() = Unit
}
