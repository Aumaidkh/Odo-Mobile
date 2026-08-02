package com.hopcape.odo.feature.profile.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.feature.profile.presentation.state.Loadable

/**
 * What the owner did on the profile home.
 *
 * One event, because one tap here is worth counting on its own: taking the sign-in prompt
 * is the funnel step that has no screen of its own to count it. Everything else is counted
 * where it lands — a settings row by that row's ViewModel, an export by the export sheet —
 * so no action reaches a dashboard twice.
 */
internal sealed interface ProfileEvent {

    /** The sign-in prompt was taken. */
    data object SignInStarted : ProfileEvent
}

/**
 * What the profile home shows once the read lands.
 *
 * Values, not sentences: the summaries under each preference row ("4 on", "km") are built
 * in the UI from these, so the copy stays in `strings.xml` with the rest of the screen's.
 *
 * [name] and [city] are nullable because a profile legitimately has neither yet — and the
 * missing city is worth a prompt rather than a blank, since it is what turns price checks
 * on.
 */
@Immutable
internal data class ProfileContent(
    val name: String?,
    val city: String?,
    val avatarPath: String?,
    val isPro: Boolean,
    val isSignedIn: Boolean,
    val notificationTopicsOn: Int,
    val distanceUnit: DistanceUnit,
    val fuelEfficiencyUnit: FuelEfficiencyUnit,
    val theme: ThemePreference,
)

/** Screen state for the profile home. */
@Immutable
internal data class ProfileUiState(
    val content: Loadable<ProfileContent> = Loadable.Loading,
    /** The app's version, as the platform reports it. */
    val version: String = "",
)
