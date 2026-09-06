@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hopcape.odo.web.admin.ui

import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

/**
 * An anchor with a `download` attribute, and a hidden file input.
 *
 * Both gestures are ordinary DOM, written against the bindings. Two small pieces
 * drop into JavaScript because Wasm has no typed binding for them: building a blob
 * URL, and reading `FileReader.result`, which arrives as an untyped JS value.
 */
actual fun browserFiles(): BrowserFiles = DomBrowserFiles

private object DomBrowserFiles : BrowserFiles {

    override fun download(fileName: String, mimeType: String, text: String) {
        // Wrapped, like every other DOM reach in this app: a browser that refuses
        // the blob should leave the panel as it was, not blank the page.
        runCatching { saveTextFile(fileName, mimeType, text) }
    }

    override fun pickText(accept: String, onPicked: (text: String) -> Unit) {
        runCatching {
            // Never added to the page. It exists for the length of one click, and
            // an admin who cancels leaves nothing behind.
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = accept
            input.onchange = {
                val file = input.files?.item(0)
                if (file != null) {
                    val reader = FileReader()
                    reader.onload = { onPicked(readerText(reader)) }
                    reader.readAsText(file)
                }
            }
            input.click()
        }
    }
}

/**
 * A blob URL rather than a `data:` URL. A data URL carries the whole file in the
 * href, and a catalog of a few thousand models is past what some browsers accept
 * there — silently, by doing nothing.
 */
private fun saveTextFile(fileName: String, mimeType: String, text: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([text], { type: mimeType }));
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }""",
)

/** `result` is a string for a file read with `readAsText`, but is typed as neither. */
private fun readerText(reader: FileReader): String = js("String(reader.result)")
