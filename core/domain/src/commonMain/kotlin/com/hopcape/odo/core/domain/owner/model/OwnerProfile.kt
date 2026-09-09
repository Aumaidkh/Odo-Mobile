package com.hopcape.odo.core.domain.owner.model

import arrow.core.getOrElse
import kotlin.time.Instant

/**
 * The Owner aggregate root — who this person is to Odo, and why they came.
 *
 * Deliberately small. It holds only what a *feature* reads back: the name used to greet
 * them and whether first-run setup is finished. The owner's goals are not here — they live
 * in `profile_answers`, which is the only shape that holds more than one (#394). The rest of
 * the server's `profiles` row (home city, preferred language)
 * is either owned by auth or not asked for yet, and gets modelled when something actually
 * reads it.
 *
 * [phone] is the exception among those, and it is here because nothing else could put it on
 * the server. It is not edited — auth proves it and this only carries it — so no screen
 * takes it and every whole-profile write leaves it alone (see [withPhone]).
 *
 * [name] is nullable because a profile legitimately exists without one: the server creates
 * the row by trigger at signup, before onboarding has asked anything. The rule that
 * *onboarding* must collect a name before finishing is onboarding's, so it lives in that
 * feature's use case — not here.
 */
class OwnerProfile private constructor(
    val id: OwnerId,
    val name: OwnerName?,
    val onboardingCompletedAt: Instant?,
    val city: String?,
    val email: OwnerEmail?,
    val avatarPath: String?,
    val sharesPricesAnonymously: Boolean,
    val phone: PhoneNumber?,
) {
    /**
     * Rename the owner. Takes an already-validated [OwnerName], so there is nothing left
     * to check here — the same split as [new].
     */
    fun withName(name: OwnerName): OwnerProfile =
        OwnerProfile(id, name, onboardingCompletedAt, city, email, avatarPath, sharesPricesAnonymously, phone)

    /** Set or clear the contact email. Null clears it; Odo never requires an address. */
    fun withEmail(email: OwnerEmail?): OwnerProfile =
        OwnerProfile(id, name, onboardingCompletedAt, city, email, avatarPath, sharesPricesAnonymously, phone)

    /**
     * Whether the prices on this owner's service logs may feed the city benchmark.
     *
     * On the profile rather than in
     * [AppSettings][com.hopcape.odo.core.domain.settings.model.AppSettings] because it is
     * the *server* that has to honour it: the benchmark is aggregated there, from rows that
     * belong to the account, so the answer has to travel with the account rather than sit
     * on one phone. A device with no account still reads it, from the local placeholder row.
     *
     * Turning it off does not withdraw prices already aggregated into a published benchmark
     * — those are anonymous averages with nothing left to trace back — so the copy on the
     * switch promises what happens next, not what has already happened.
     */
    fun withPriceSharing(shares: Boolean): OwnerProfile =
        OwnerProfile(id, name, onboardingCompletedAt, city, email, avatarPath, shares, phone)

    /**
     * Point at the owner's profile photo, or clear it with null.
     *
     * A storage key for a file already copied into the app's own storage, not a picked
     * URI: the URI stops working after the app restarts. Copying is the caller's job
     * (`:core:platform`), because the domain does not touch files.
     */
    fun withAvatar(avatarPath: String?): OwnerProfile =
        OwnerProfile(id, name, onboardingCompletedAt, city, email, avatarPath?.ifBlank { null }, sharesPricesAnonymously, phone)

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
        OwnerProfile(id, name, onboardingCompletedAt, city?.trim()?.ifBlank { null }, email, avatarPath, sharesPricesAnonymously, phone)

    /**
     * Carry the number the session proved.
     *
     * Not a setting and not something anyone types here: auth is the only authority on which
     * number this account is, so the only caller is the sign-in path. It exists so the number
     * reaches the server, which has no other way of learning it after the account was made.
     */
    fun withPhone(phone: PhoneNumber?): OwnerProfile =
        OwnerProfile(id, name, onboardingCompletedAt, city, email, avatarPath, sharesPricesAnonymously, phone)

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
        if (hasCompletedOnboarding) this else OwnerProfile(id, name, at, city, email, avatarPath, sharesPricesAnonymously, phone)

    companion object {
        /**
         * A freshly answered profile. Takes value objects, not raw input: validating a
         * name is [OwnerName]'s job and deciding that a goal is mandatory is the calling
         * feature's, so by the time this is reached there is nothing left to check.
         */
        fun new(id: OwnerId, name: OwnerName): OwnerProfile = OwnerProfile(
            id = id,
            name = name,
            onboardingCompletedAt = null,
            city = null,
            email = null,
            avatarPath = null,
            // On by default, and onboarding's consent card says so before this is reached.
            sharesPricesAnonymously = true,
            // Setup does not ask for it and cannot: the number is only known once a session
            // proves it, which may not have happened yet.
            phone = null,
        )

        /**
         * Rehydrate from already-persisted, trusted data (the local DB, or a row pulled
         * from the server).
         *
         * Nulls are accepted here because a signup-created row genuinely has no name yet,
         * and refusing to load it would make the app unable to read its own database. A *present but invalid* value is different — that means corruption, so
         * it fails fast rather than silently repairing itself, matching
         * [com.hopcape.odo.core.domain.car.model.Car.reconstitute].
         */
        fun reconstitute(
            id: OwnerId,
            name: String?,
            onboardingCompletedAt: Instant?,
            city: String? = null,
            email: String? = null,
            avatarPath: String? = null,
            sharesPricesAnonymously: Boolean = true,
            phone: String? = null,
        ): OwnerProfile = OwnerProfile(
            id = id,
            name = name?.let {
                OwnerName.of(it).getOrElse { error("corrupt profile.name for ${id.value}") }
            },
            onboardingCompletedAt = onboardingCompletedAt,
            city = city,
            email = OwnerEmail.of(email).getOrElse { error("corrupt profile.email for ${id.value}") },
            avatarPath = avatarPath,
            sharesPricesAnonymously = sharesPricesAnonymously,
            // Dropped rather than fatal, unlike the name above. This value can arrive from
            // the server, and the number is carried rather than read — nothing computes
            // anything from it, and the next sign-in writes it again. Refusing to load the
            // profile at all would turn one odd server value into an app that cannot open.
            phone = PhoneNumber.of(phone).getOrNull(),
        )
    }
}
