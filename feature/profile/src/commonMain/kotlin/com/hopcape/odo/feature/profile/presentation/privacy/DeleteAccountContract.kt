package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/** What the owner did on the way to erasing their account. */
internal sealed interface DeleteAccountEvent {

    /** The typed confirmation phrase, as it is typed. */
    data class PhraseChanged(val phrase: String) : DeleteAccountEvent

    /** "Yes, delete it" on the confirmation. Starts the code, or the wipe on a local device. */
    data object Confirmed : DeleteAccountEvent

    /** The six digits, as they are typed. */
    data class CodeChanged(val code: String) : DeleteAccountEvent

    /** The code is complete and the owner wants it checked. */
    data object CodeSubmitted : DeleteAccountEvent

    /** "Send it again" — a fresh code to the same number. */
    data object ResendRequested : DeleteAccountEvent

    /**
     * Try the local wipe again, after the account was erased but this device kept its copy.
     *
     * The only retry offered from [DeleteAccountStep.LocalWipeFailed]: retrying the erase
     * would do nothing, because there is nothing left on the server to erase.
     */
    data object LocalWipeRetried : DeleteAccountEvent
}

/** The one thing this flow can ask the app to do. */
internal sealed interface DeleteAccountEffect {

    /**
     * Everything is gone. The app is back to first run, and the whole in-app stack goes with
     * it — every screen behind this one is about data that no longer exists.
     */
    data object Deleted : DeleteAccountEffect
}

/** How far the erase has got. */
internal enum class DeleteAccountStep {

    /** Stating what will go, before anything happens. */
    Confirm,

    /** Sending the code, or checking one. Nothing is cancellable from here on. */
    Working,

    /**
     * Waiting for the six digits.
     *
     * Reached only when there is an account to erase. The server refuses proof older than
     * ten minutes, so an ordinary session is not enough and the number is proved again.
     */
    Verify,

    /**
     * The account is gone and this phone still has a copy.
     *
     * Its own step because it is neither success nor failure: nothing can be recovered by
     * retrying the erase, and the only useful offer is to retry the wipe.
     */
    LocalWipeFailed,
}

/**
 * Screen state for the account deletion.
 *
 * [phoneNumber] is masked for display and is null on a device that never signed in — which
 * is also what makes the flow skip [DeleteAccountStep.Verify] entirely. Nobody should have to
 * prove a number to delete a database that only ever lived on their phone.
 */
@Immutable
internal data class DeleteAccountUiState(
    val step: DeleteAccountStep = DeleteAccountStep.Confirm,
    val phoneNumber: String? = null,
    /** What the owner has typed into the confirmation field so far. */
    val phrase: String = "",
    val code: String = "",
    val error: UiText? = null,
) {
    /** Whether erasing will reach a server at all. */
    val hasAccount: Boolean get() = phoneNumber != null

    /**
     * The phrase has been typed exactly, so the delete button may be pressed.
     *
     * Case-insensitive and trimmed. The point of typing it is to make the action deliberate,
     * not to test anyone's shift key — refusing "delete my account" in lower case would be
     * a puzzle, and a puzzle is not the same thing as a confirmation.
     */
    val canDelete: Boolean get() = phrase.trim().equals(CONFIRM_PHRASE, ignoreCase = true)

    /** The code is the full six digits, so there is something to submit. */
    val canSubmit: Boolean get() = code.length == CODE_LENGTH

    companion object {
        /** Firebase's SMS codes are always six digits. */
        const val CODE_LENGTH: Int = 6

        /**
         * What has to be typed before the account can go.
         *
         * Deliberately the same words as the row that opened this screen, so there is nothing
         * to work out — and deliberately not localised alongside the rest of the copy, because
         * the string the owner types and the string the app compares must be the same one. A
         * translated button label with an English comparison would lock out every non-English
         * reader; keeping both in this constant makes that impossible.
         */
        const val CONFIRM_PHRASE: String = "Delete my account"
    }
}
