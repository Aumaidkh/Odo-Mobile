package com.hopcape.odo.feature.profile.domain.usecase

import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.subscription.SubscriptionStatusSource
import com.hopcape.odo.feature.profile.domain.model.ProfileSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The profile as every one of its screens sees it: the owner's details, their plan, whether
 * this device is signed in, and the device's settings.
 *
 * The plan is observed. A purchase completes on the paywall and the owner comes back here,
 * so the card has to change under them without a reopen.
 *
 * Session and the verified number are still read per emission rather than observed, because
 * neither can change while the screen is open — there is no sign-in flow that returns here.
 * The ports grow a stream when that stops being true, and only this file changes.
 */
internal class ObserveProfileUseCase(
    private val profiles: OwnerProfileRepository,
    private val settings: AppSettingsRepository,
    private val entitlements: EntitlementSource,
    private val subscription: SubscriptionStatusSource,
    private val session: SessionStatusProvider,
    private val account: VerifiedAccount,
) {
    operator fun invoke(): Flow<ProfileSnapshot> =
        combine(
            profiles.observe(),
            settings.observe(),
            entitlements.observe(),
            subscription.observe(),
        ) { profile, appSettings, entitlements, subscription ->
            ProfileSnapshot(
                name = profile?.name?.value,
                email = profile?.email?.value,
                city = profile?.city,
                phoneNumber = account.verifiedNumber()?.value,
                avatarPath = profile?.avatarPath,
                isPro = entitlements.plan == Plan.PRO,
                subscription = subscription,
                isSignedIn = session.isSignedIn(),
                settings = appSettings,
            )
        }
}
