package com.hopcape.odo.core.platform.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Hands a stored image to another app, with [caption] as the message beside it.
 *
 * Separate from [rememberFileSharer] rather than a flag on it, because sharing a picture is
 * a different errand from sharing a document: it carries a caption, and it is usually aimed
 * at one app rather than offered to all of them.
 *
 * [preferredApp] names a package to open directly — [WHATSAPP_PACKAGE] is the one Odo uses.
 * When it is not installed, or cannot take the image, the system chooser is offered instead.
 * An owner who tapped a share button gets a share sheet rather than nothing happening.
 *
 * The file must already be in [EXPORT_DIRECTORY]; that is the only directory the app exports.
 *
 * @return a function taking the stored image's key, the caption, and the package to aim at.
 */
@Composable
expect fun rememberImageSharer(): (storageKey: String, caption: String, preferredApp: String?) -> Unit

/**
 * PNG bytes for this bitmap, or null when the platform could not encode it.
 *
 * Null rather than an exception: the caller is a share button, and there is nothing useful to
 * say to an owner about an encoder. It shows the same "could not do that" it shows for a
 * failed write.
 */
expect suspend fun ImageBitmap.toPngBytes(): ByteArray?

/**
 * WhatsApp's Android package.
 *
 * Named because Scene 2 of the advisory plan is a WhatsApp share and nothing else — the card
 * goes to a family group, which is where the next three owners come from. Ignored on iOS,
 * which offers no way to aim the share sheet at one app.
 */
const val WHATSAPP_PACKAGE: String = "com.whatsapp"
