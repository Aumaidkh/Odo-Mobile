package com.hopcape.odo.feature.profile.domain.usecase

import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.entitlement.ProEntitlement
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.feature.profile.domain.model.ProfileSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The profile as every one of its screens sees it: the owner's details, their plan, whether
 * this device is signed in, and the device's settings.
 *
 * Entitlement, session and the verified number are read per emission rather than observed,
 * because none of them can change while the screen is open — a subscription is bought
 * outside the app and there is no sign-in flow that returns here. The ports grow a stream
 * when that stops being true, and only this file changes.
 */
internal class ObserveProfileUseCase(
    private val profiles: OwnerProfileRepository,
    private val settings: AppSettingsRepository,
    private val entitlement: ProEntitlement,
    private val session: SessionStatusProvider,
    private val account: VerifiedAccount,
) {
    operator fun invoke(): Flow<ProfileSnapshot> =
        combine(profiles.observe(), settings.observe()) { profile, appSettings -> profile to appSettings }
            .map { (profile, appSettings) ->
                ProfileSnapshot(
                    name = profile?.name?.value,
                    email = profile?.email?.value,
                    city = profile?.city,
                    phoneNumber = account.verifiedNumber()?.value,
                    avatarPath = profile?.avatarPath,
                    isPro = entitlement.isPro(),
                    isSignedIn = session.isSignedIn(),
                    settings = appSettings,
                )
            }
}
