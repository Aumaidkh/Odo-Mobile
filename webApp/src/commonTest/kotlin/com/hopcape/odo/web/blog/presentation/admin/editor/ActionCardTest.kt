package com.hopcape.odo.web.blog.presentation.admin.editor

import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The action card's five fields.
 *
 * The card shipped with only its title reachable: the editor read `heading` and
 * wrote `heading`, so the description and the button label could be set once in
 * code and never changed, and the button's destination was a constant. A card
 * that always goes to the same place cannot send a reader anywhere useful, which
 * is the only thing the block is for.
 */
class ActionCardTest {

    private val card = ArticleBlock.AppShowcase(
        heading = "Odo's Cost Tracker does the subtraction",
        body = "Every fill goes in with an odometer reading.",
        callToAction = "See your real cost per km",
    )

    @Test
    fun every_field_can_be_edited() {
        val edited = card
            .withField(ShowcaseField.TITLE, "New title")
            .withField(ShowcaseField.BODY, "New body")
            .withField(ShowcaseField.CTA_LABEL, "Get Odo")
            .withField(ShowcaseField.CTA_LINK, "https://odoapp.in/costs")
            .withField(ShowcaseField.SCREENSHOT, "https://cdn/shot.png")

        assertEquals("New title", edited.heading)
        assertEquals("New body", edited.body)
        assertEquals("Get Odo", edited.callToAction)
        assertEquals("https://odoapp.in/costs", edited.link)
        assertEquals("https://cdn/shot.png", edited.screenshot)
    }

    @Test
    fun each_field_reads_back_what_was_written() {
        ShowcaseField.entries.forEach { field ->
            val edited = card.withField(field, "value")
            assertEquals("value", edited.field(field), "$field did not read back")
        }
    }

    @Test
    fun clearing_the_screenshot_means_no_screenshot() {
        val cleared = card.withField(ShowcaseField.SCREENSHOT, "https://cdn/shot.png")
            .withField(ShowcaseField.SCREENSHOT, "")

        // Null rather than blank: the renderer checks for null to decide between
        // the picture and the slot line.
        assertNull(cleared.screenshot)
    }

    @Test
    fun a_new_card_has_a_blank_link() {
        val fresh = BlockKind.ACTION.empty() as ArticleBlock.AppShowcase

        // Blank is the signal for "use the Play listing". If this ever defaults to
        // a literal URL, an author clearing the box would get a broken button
        // instead of the sensible fallback.
        assertEquals("", fresh.link)
    }

    @Test
    fun editing_one_card_leaves_the_others_alone() {
        val blocks = listOf(card, card.withField(ShowcaseField.TITLE, "Second"))
        val edited = blocks.mapIndexed { index, block ->
            if (index == 1 && block is ArticleBlock.AppShowcase) {
                block.withField(ShowcaseField.CTA_LINK, "https://odoapp.in")
            } else {
                block
            }
        }

        assertEquals("", (edited[0] as ArticleBlock.AppShowcase).link)
        assertEquals("https://odoapp.in", (edited[1] as ArticleBlock.AppShowcase).link)
    }
}
