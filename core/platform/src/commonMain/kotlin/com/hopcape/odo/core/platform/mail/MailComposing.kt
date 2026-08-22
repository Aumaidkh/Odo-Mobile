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

/**
 * The draft as a `mailto:` URI — the one form every mail app on both platforms reads.
 *
 * Shared rather than written per platform, which is how the two came apart in the first
 * place: iOS put the subject and body in the URI while Android sent them as intent extras,
 * and several Android mail apps do not read extras on a `SENDTO` intent. Those drafts
 * opened with an empty subject line and an empty box, so a report that should have said
 * which of three forms it came from arrived untitled and blank.
 *
 * The address keeps its `@` — escaping it gives `support%40odoapp.in`, which is a legal
 * escape and still not what some clients put in the To field. The subject and body are
 * escaped in full, and have to be: a body contains newlines and `&`, and both end the draft
 * early if they reach the URL raw.
 */
internal fun MailDraft.toMailtoUri(): String =
    "mailto:${to.percentEncoded(allow = "@")}" +
        "?subject=${subject.percentEncoded()}" +
        "&body=${body.percentEncoded()}"

/**
 * Percent-encodes one `mailto:` field, per RFC 3986.
 *
 * Written out rather than handed to a platform API so both actuals produce byte-identical
 * URIs and there is one thing to test. Everything outside the unreserved set is escaped
 * unless [allow] names it, which is stricter than necessary but never wrong.
 *
 * Encoding the UTF-8 bytes rather than the characters is what makes a body in any script
 * survive the trip.
 */
private fun String.percentEncoded(allow: String = ""): String = buildString {
    for (byte in this@percentEncoded.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        val unreserved = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
            char in "-_.~" || char in allow
        if (unreserved) {
            append(char)
        } else {
            append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
