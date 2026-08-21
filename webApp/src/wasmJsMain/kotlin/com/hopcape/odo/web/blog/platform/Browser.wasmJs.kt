package com.hopcape.odo.web.blog.platform

import com.hopcape.odo.web.blog.domain.UploadRequest
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * `noopener` is not optional: without it the opened tab gets a handle on this
 * one through `window.opener` and can navigate it somewhere else.
 */
actual fun openExternal(url: String) {
    window.open(url, target = "_blank", features = "noopener,noreferrer")
}

/**
 * The async clipboard API. It rejects when the document is not focused or the
 * page is not on a secure origin, and there is nothing useful to tell the author
 * in either case — the button simply does not report success, which is what the
 * caller already handles.
 */
actual fun copyToClipboard(text: String) {
    runCatching { window.navigator.clipboard.writeText(text) }
}

actual fun setDocumentTitle(title: String) {
    document.title = title
}

/**
 * A file input that never joins the document.
 *
 * `click()` on a detached input still opens the picker, and keeping it out of the
 * DOM means no hidden element to lay out, style around or trip over. It is
 * created fresh each time so a second upload is not blocked by the first
 * selection still being on the element.
 *
 * The bytes are read here rather than passed along as a handle, so the port stays
 * common code and the repository never has to know what a browser `File` is.
 */
actual fun pickImage(onPicked: (UploadRequest) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/png,image/jpeg"
    input.onchange = {
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                val buffer = reader.result as? ArrayBuffer
                if (buffer != null) {
                    val view = Int8Array(buffer)
                    onPicked(
                        UploadRequest(
                            name = file.name,
                            mimeType = file.type.ifBlank { "image/png" },
                            bytes = ByteArray(view.length) { index -> view[index] },
                        ),
                    )
                }
                Unit
            }
            reader.readAsArrayBuffer(file)
        }
        Unit
    }
    input.click()
}

actual fun decodeImageBytes(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
    try {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        // Skia throws rather than returning null on a truncated or non-image
        // body, and a broken upload is not worth an exception reaching the UI.
        null
    }
