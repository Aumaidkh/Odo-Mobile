package com.hopcape.odo.core.platform.notification

/**
 * Port that shows a detected fill on the lock screen and lets the owner act on it there.
 *
 * The whole promise of detection is a fill logged without opening the app, so this is where
 * that promise is kept or broken. Confirm has to work from the notification itself; anything
 * that opens the app first has already cost the owner what the feature was saving them.
 *
 * A no-op implementation is correct on any platform without this kind of notification. The
 * fill is still in the app when the owner next opens it.
 */
interface DetectedFillNotifier {

    /**
     * Show [notice], with Confirm and Edit.
     *
     * Confirm calls [onConfirm] with the notice's [DetectedFillNotice.draftPayload] and never
     * opens the app; Edit opens it at the confirm step. The callback is passed in rather than
     * held because the object that can write a fill is a use case with repositories, and
     * nothing about posting a notification should know what one is.
     */
    suspend fun show(notice: DetectedFillNotice, onConfirm: suspend (String) -> Unit)

    /** Take the notification down — after it has been acted on, or when detection is off. */
    fun dismiss(noticeId: String)
}

/**
 * A detected fill, already turned into the text the notification shows.
 *
 * Formatting happens before this: a notification is drawn by the system from platform
 * resources, and the feature's copy lives in Compose resources the system cannot reach. So
 * the caller renders the lines and this carries them.
 *
 * [draftPayload] is the draft, serialized. It travels through the notification because the
 * process may be gone by the time the owner taps — they paid at the pump, put the phone away,
 * and came back to it at a red light — and a callback held in memory would not survive that.
 */
data class DetectedFillNotice(
    val id: String,
    val title: String,
    val body: String,
    val confirmLabel: String,
    val editLabel: String,
    val draftPayload: String,
    /**
     * Whether the primary action can write the fill on its own.
     *
     * False when the draft is still missing something only the owner can supply — today that
     * means no quantity, because they have not set a fuel price and nothing can divide the
     * amount into litres. The button then opens the app at the confirm step instead of
     * writing, and its label says so.
     *
     * The alternative was letting Confirm attempt a write that always fails, which is what
     * this app did: the tap dismissed the notification, the fill was never stored, and the
     * owner was told nothing. A button that cannot do the thing it names must not name it.
     */
    val oneTapConfirm: Boolean = true,
)
