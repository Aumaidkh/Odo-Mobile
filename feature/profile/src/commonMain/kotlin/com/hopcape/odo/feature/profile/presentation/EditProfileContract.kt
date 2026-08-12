package com.hopcape.odo.feature.profile.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.feature.profile.presentation.state.FormField
import com.hopcape.odo.feature.profile.presentation.state.Submission

/** What the owner did on the edit-profile screen. */
internal sealed interface EditProfileEvent {

    data class NameChanged(val value: String) : EditProfileEvent

    data class EmailChanged(val value: String) : EditProfileEvent

    data class CityChanged(val value: String?) : EditProfileEvent

    /** A photo came back from the picker, as the reference the picker returned. */
    data class PhotoPicked(val pickedRef: String) : EditProfileEvent

    data object Save : EditProfileEvent

    /** "Delete account" was tapped — ask before doing anything. */
    data object DeleteRequested : EditProfileEvent

    /** The confirmation was dismissed. */
    data object DeleteDismissed : EditProfileEvent

    /** The confirmation was accepted. Everything on this device goes. */
    data object DeleteConfirmed : EditProfileEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface EditProfileEffect {

    /** The details were stored — close the editor. */
    data object Saved : EditProfileEffect

    /** The owner's data is gone — leave for first-run setup. */
    data object DataDeleted : EditProfileEffect
}

/**
 * Display state for the edit-profile screen.
 *
 * The fields are [FormField]s because two of them can be rejected and the screen has to say
 * which. [deletion] is its own [Submission]: a failed wipe must not read as a failed save,
 * and the two can never be in flight together.
 */
@Immutable
internal data class EditProfileUiState(
    val name: FormField<String> = FormField(),
    val email: FormField<String> = FormField(),
    val city: FormField<String> = FormField(),
    val cities: List<String> = emptyList(),
    val avatarPath: String? = null,
    val isSignedIn: Boolean = false,
    val submission: Submission = Submission.Idle,
    val deletion: Submission = Submission.Idle,
    val confirmingDelete: Boolean = false,
) {
    /** Nothing to save while a write is already running. */
    val canSave: Boolean get() = !submission.isInFlight && !deletion.isInFlight
}
