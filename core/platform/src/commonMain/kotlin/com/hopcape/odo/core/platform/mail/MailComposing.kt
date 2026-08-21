package com.hopcape.odo.core.platform.mail

import androidx.compose.runtime.Composable

/**
 * An email the owner is about to send, filled in before the mail app opens.
 *
 * The owner edits all of it afterwards. Nothing here is final, and nothing is sent by Odo —
 * the mail app sends it, from the owner's own account, when they press send.
 *
 * @property to the recipient address.
 * @property subject the subject line. Support rows put the topic here so a reply thread is
 *   sortable without opening it.
 * @property body the starting body text, usually the owner's message followed by the build
 *   and device the report is about.
 */
data class MailDraft(
    val to: String,
    val subject: String,
    val body: String,
)

/**
 * Opens the mail app on a prefilled draft.
 *
 * A composable rather than an injected port, for the same reason as [
 * com.hopcape.odo.core.platform.share.rememberTextSharer]: presenting the composer needs
 * whatever is hosting the UI, and no Koin singleton can hold that without leaking it.
 *
 * Fire and forget. Whether the owner sends the mail, edits it first or abandons it is not
 * reported back, and nothing in Odo depends on the answer — there is no ticket record on
 * this side to update.
 *
 * A device with no mail app does nothing at all. There is nothing useful to say to someone
 * who asked to send mail from a phone that cannot, and failing quietly leaves the screen
 * they were on intact.
 *
 * @return a function to call with the draft to open.
 */
@Composable
expect fun rememberMailComposer(): (MailDraft) -> Unit
