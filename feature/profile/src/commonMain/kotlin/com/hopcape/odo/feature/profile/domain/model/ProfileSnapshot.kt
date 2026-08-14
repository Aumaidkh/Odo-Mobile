package com.hopcape.odo.feature.profile.domain.model

import com.hopcape.odo.core.domain.settings.model.AppSettings

/**
 * Everything the profile screens read, in one value.
 *
 * Four sources answer at once — who the owner is, what they are entitled to, whether this
 * device has a session, and how they want the app to behave — and every profile surface
 * needs some mix of them. Reading them together means a screen never renders half a
 * profile while the rest arrives.
 *
 * [name] and the rest are nullable because a profile legitimately has none of them yet: a
 * signup-created row carries no name, onboarding never asks for a city, and nothing here
 * requires an email or a photo.
 *
 * [phoneNumber] comes from the auth provider, not from the stored profile — nothing in Odo
 * keeps it (see [VerifiedAccount][com.hopcape.odo.core.domain.auth.VerifiedAccount]). It is
 * null on a device that never signed in, in E.164 otherwise.
 */
internal data class ProfileSnapshot(
    val name: String?,
    val email: String?,
    val city: String?,
    val phoneNumber: String?,
    val avatarPath: String?,
    val isPro: Boolean,
    val isSignedIn: Boolean,
    val settings: AppSettings,
)
