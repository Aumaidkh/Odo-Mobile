package com.hopcape.odo.web.core.platform


/**
 * The three things this app needs from the browser that are not drawing.
 *
 * Declared as `expect` even though there is exactly one target, because the
 * alternative is a screen in `commonMain` reaching for `window` — and the moment
 * one does, every screen is untestable off-browser and the module has no seam
 * left to put a preview behind.
 */

/** Opens a URL in a new tab. Used for the Play listing and the legal pages. */
expect fun openExternal(url: String)

/** Puts [text] on the clipboard. The CMS copies a published post's URL. */
expect fun copyToClipboard(text: String)

/**
 * Sets the browser tab's title.
 *
 * Called on every navigation. It is the one piece of page metadata a canvas app
 * can still get right, and it is what a reader sees in a pinned tab and in their
 * history.
 */
expect fun setDocumentTitle(title: String)

/**
 * Opens the system file picker and hands back what was chosen.
 *
 * A callback rather than a suspend function because the browser's own API is
 * event-driven and a cancelled picker fires nothing at all — there is no event
 * for "the reader closed the dialog", so a suspending version would hang forever
 * on the one path that happens most.
 */
expect fun pickImage(onPicked: (UploadRequest) -> Unit)

/** The Play listing, tagged so installs from the blog are countable. */
const val PLAY_LISTING: String =
    "https://play.google.com/store/apps/details?id=com.hopcape.odo&referrer=utm_source%3Dblog"

/**
 * Encoded image bytes into something Compose can draw.
 *
 * Behind the seam for the same reason as everything else in this file: decoding
 * is Skia's job on this target and somebody else's on the next one, and a
 * composable that knows which is a composable that cannot be tested.
 *
 * Returns null on anything it cannot read. A screenshot that fails to decode
 * should cost the article a picture, not the page.
 */
expect fun decodeImageBytes(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap?
