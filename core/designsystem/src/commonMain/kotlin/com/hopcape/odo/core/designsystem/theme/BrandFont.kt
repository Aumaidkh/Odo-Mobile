package com.hopcape.odo.core.designsystem.theme

import com.hopcape.odo.core.designsystem.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The brand face as raw font bytes, for the documents Odo produces outside Compose.
 *
 * A printed record is laid out by a browser engine rather than by Compose, so it cannot use
 * the [FontFamily][androidx.compose.ui.text.font.FontFamily] the theme builds — it needs the
 * file itself to embed. The font resources are private to this module, so this is the one way
 * out of it, and the design system stays the only place that knows what Odo is set in.
 *
 * Two weights, not six. A document uses a regular and a bold; carrying the other four would
 * add a quarter of a megabyte to every file the owner sends and change nothing on the page.
 */
object BrandFont {

    /** The body weight — every line of a document that is not a heading. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun regularBytes(): ByteArray = Res.readBytes(REGULAR_PATH)

    /** Headings, figures and anything the eye is meant to land on first. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun boldBytes(): ByteArray = Res.readBytes(BOLD_PATH)

    /** The family name a document should declare its `@font-face` under. */
    const val FAMILY_NAME: String = "Odo Sans"

    private const val REGULAR_PATH = "font/dm_sans_regular.ttf"
    private const val BOLD_PATH = "font/dm_sans_bold.ttf"
}
