package com.hopcape.odo.core.domain.owner.model

import arrow.core.getOrElse
import kotlin.time.Instant

/**
 * The Owner aggregate root — who this person is to Odo, and why they came.
 *
 * Deliberately small. It holds only what a *feature* reads back: the name used to greet
 * them, the goal that decides where onboarding drops them (PRD §5.1), and whether first-run
 * setup is finished. Everything else on the server's `profiles` row (phone, home city,
 * preferred language) is either owned by auth or not asked for yet, and gets modelled when
 * something actually reads it.
 *
 * [name] and [goal] are nullable because a profile legitimately exists without them: the
 * server creates the row by trigger at signup, before onboarding has asked anything. The
 * rule that *onboarding* must collect both before finishing is onboarding's, so it lives in
 * that feature's use case — not here. [new] simply refuses to build a half-answered profile
 * by taking both, already validated.
 */
class OwnerProfile private constructor(
    val id: OwnerId,
    val name: OwnerName?,
    val goal: OnboardingGoal?,
    val onboardingCompletedAt: Instant?,
    val city: String?,
) {
    /**
     * Set the owner's home city — the key every fairness benchmark is looked up by
     * ("Pune average"). Null until they set it on their profile: onboarding deliberately
     * does not ask, because a wrong city produces confident wrong verdicts and the flow
     * is already long. Blank input clears it rather than storing an empty string.
     *
     * A plain `String`, not a value object, because that is what the fairness models and
     * the benchmark RPC take end to end; wrapping it here would only unwrap it there.
     */
    fun withCity(city: String?): OwnerProfile =
        OwnerProfile(id, name, goal, onboardingCompletedAt, city?.trim()?.ifBlank { null })

    /**
     * Whether first-run setup is finished. Stored as a timestamp rather than a boolean
     * because *when* someone onboarded is worth knowing (funnel analysis, "new user"
     * cohorts) and a boolean throws that away for no saving.
     */
    val hasCompletedOnboarding: Boolean get() = onboardingCompletedAt != null

    /**
     * Mark setup finished at [at]. Returns a new profile rather than mutating: the
     * aggregate owns the transition, and the repository's only job is to persist whatever
     * it hands back.
     *
     * Re-completing is a no-op — the first completion is the one that counts, so a repeated
     * call can't rewrite history.
     */
    fun completeOnboarding(at: Instant): OwnerProfile =
        if (hasCompletedOnboarding) this else OwnerProfile(id, name, goal, at, city)

    companion object {
        /**
         * A freshly answered profile. Takes value objects, not raw input: validating a
         * name is [OwnerName]'s job and deciding that a goal is mandatory is the calling
         * feature's, so by the time this is reached there is nothing left to check.
         */
        fun new(id: OwnerId, name: OwnerName, goal: OnboardingGoal): OwnerProfile =
            OwnerProfile(id = id, name = name, goal = goal, onboardingCompletedAt = null, city = null)

        /**
         * Rehydrate from already-persisted, trusted data (the local DB, or a row pulled
         * from the server).
         *
         * Nulls are accepted here because a signup-created row genuinely has no name or
         * goal yet, and refusing to load it would make the app unable to read its own
         * database. A *present but invalid* value is different — that means corruption, so
         * it fails fast rather than silently repairing itself, matching
         * [com.hopcape.odo.core.domain.car.model.Car.reconstitute].
         */
        fun reconstitute(
            id: OwnerId,
            name: String?,
            goal: OnboardingGoal?,
            onboardingCompletedAt: Instant?,
            city: String? = null,
        ): OwnerProfile = OwnerProfile(
            id = id,
            name = name?.let {
                OwnerName.of(it).getOrElse { error("corrupt profile.name for ${id.value}") }
            },
            goal = goal,
            onboardingCompletedAt = onboardingCompletedAt,
            city = city,
        )
    }
}
