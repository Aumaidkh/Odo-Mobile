package com.hopcape.odo.feature.profile.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.feature.profile.presentation.state.Loadable
import kotlin.time.Instant

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
    /** The app's version, as the platform reports it — includes the build type's suffix. */
    val version: String = "",
    /**
     * The build number behind [version], shown next to it on debug and stage builds and
     * `null` on release. A tester quoting "1.0.0-beta01-stage" says nothing about which
     * build they are on; an owner on the store has one build per version and does not
     * need the number.
     */
    val buildNumber: Long? = null,
    /** Where background sync has got to. See [SyncStatus]. */
    val sync: SyncStatus = SyncStatus(),
)

/**
 * What the profile screen shows about sync.
 *
 * Diagnostics rather than product copy. SYNC_DESIGN §11 calls this the first thing anyone
 * asks for when a user says their data is missing, and it is cheap enough to build now
 * rather than after the first support ticket.
 */
internal data class SyncStatus(
    val isSyncing: Boolean = false,
    /** Rows the server has not accepted yet, across every table. */
    val pendingCount: Int = 0,
    /** When a pull last succeeded, or null on an install that has never synced. */
    val lastSyncedAt: Instant? = null,
    /**
     * Why syncing is stuck, or null when nothing is wrong. A diagnostic string from the
     * sync layer, shown as-is — this row exists to be read out during support, not to be
     * pretty.
     */
    val lastError: String? = null,
)
