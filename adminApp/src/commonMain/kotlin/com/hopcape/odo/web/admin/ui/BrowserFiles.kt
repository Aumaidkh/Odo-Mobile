package com.hopcape.odo.web.admin.ui

/**
 * Saving a file to the admin's machine, and reading one back off it.
 *
 * A seam rather than a direct reach for `document`, for the same reason
 * [ChromePreferences] is one: a composable that touches the DOM leaves the module
 * with nothing to test against off-browser.
 *
 * Both calls are fire-and-forget. Whether the admin actually chose a folder, or
 * cancelled the picker, is not reported by the browser in any way worth acting on.
 */
interface BrowserFiles {

    /** Offers [text] to the admin as a download named [fileName]. */
    fun download(fileName: String, mimeType: String, text: String)

    /**
     * Opens a file picker and reads the chosen file as text.
     *
     * [onPicked] does not run if the admin cancels, picks nothing, or the file
     * cannot be read — the caller stays as it was rather than being told about a
     * gesture that produced nothing.
     */
    fun pickText(accept: String, onPicked: (text: String) -> Unit)
}

/**
 * A file a ViewModel has decided to hand over, waiting for the screen to do it.
 *
 * A ViewModel cannot reach [BrowserFiles] — that is the DOM — so it puts the
 * finished document in its state and the screen makes the call, then says so. The
 * same shape as [com.hopcape.odo.web.core.presentation.state.UiText]: the decision
 * here, the platform there.
 */
data class DownloadFile(
    val fileName: String,
    val mimeType: String,
    val text: String,
)

/** Does neither. The binding off-browser, and in tests. */
object NoBrowserFiles : BrowserFiles {
    override fun download(fileName: String, mimeType: String, text: String) = Unit
    override fun pickText(accept: String, onPicked: (text: String) -> Unit) = Unit
}

expect fun browserFiles(): BrowserFiles
