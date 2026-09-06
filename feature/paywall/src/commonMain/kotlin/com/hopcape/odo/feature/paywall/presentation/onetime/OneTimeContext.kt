package com.hopcape.odo.feature.paywall.presentation.onetime

import com.hopcape.odo.feature.paywall.presentation.PaywallTrigger
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_footer
import com.hopcape.odo.feature.paywall.resources.pw_ot_close
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_subtitle
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_title
import com.hopcape.odo.feature.paywall.resources.pw_ot_export_footer
import com.hopcape.odo.feature.paywall.resources.pw_ot_export_sheet_subtitle
import com.hopcape.odo.feature.paywall.resources.pw_ot_export_sheet_title
import com.hopcape.odo.feature.paywall.resources.pw_ot_subtitle
import com.hopcape.odo.feature.paywall.resources.pw_ot_title
import org.jetbrains.compose.resources.StringResource

/**
 * Why the sheet was opened — which decides what it says and what it puts in front of the
 * owner.
 *
 * The same shape as [PaywallTrigger][com.hopcape.odo.feature.paywall.presentation.PaywallTrigger],
 * and for the same reason: one sheet framed by where it was reached from beats three sheets
 * that drift apart. Someone who has just run out of bill checks is not shopping for a PDF,
 * and listing one is noise on the screen where they are deciding.
 *
 * [recommended] is the offer drawn as the answer rather than as one of the options — every
 * context has one, because a list of equal-looking rows asks the owner to do the comparing.
 * It is a judgement stated once here, not a discount computed from prices the app does not
 * hold, and it is listed first: the answer belongs at the top, not in the middle.
 */
internal enum class OneTimeContext(
    val title: StringResource,
    val subtitle: StringResource,
    val offers: List<OneTimeOffer>,
    val recommended: OneTimeOffer,
    /** A line under the list, or null where there is nothing worth promising. */
    val footer: StringResource?,
    /**
     * A text button that closes the sheet, or null to leave the scrim and the back gesture
     * as the only ways out.
     *
     * Null where the owner arrived with an errand: they came for one thing, and a way to
     * decline it under the thing they came for is a row competing with the offers.
     */
    val close: StringResource? = null,
) {
    /** Reached from a bill check the owner cannot run. */
    BILL_CHECK(
        title = Res.string.pw_ot_bill_title,
        subtitle = Res.string.pw_ot_bill_subtitle,
        offers = listOf(OneTimeOffer.BILL_CHECK_PACK, OneTimeOffer.BILL_CHECK_SINGLE),
        recommended = OneTimeOffer.BILL_CHECK_PACK,
        footer = Res.string.pw_ot_bill_footer,
    ),

    /** Reached from the record export. One product, and it is the answer. */
    EXPORT(
        title = Res.string.pw_ot_export_sheet_title,
        subtitle = Res.string.pw_ot_export_sheet_subtitle,
        offers = listOf(OneTimeOffer.RECORD_EXPORT),
        recommended = OneTimeOffer.RECORD_EXPORT,
        footer = Res.string.pw_ot_export_footer,
    ),

    /** Reached from the paywall with no particular errand — everything, the pack put first. */
    GENERIC(
        title = Res.string.pw_ot_title,
        subtitle = Res.string.pw_ot_subtitle,
        offers = listOf(
            OneTimeOffer.BILL_CHECK_PACK,
            OneTimeOffer.BILL_CHECK_SINGLE,
            OneTimeOffer.RECORD_EXPORT,
        ),
        recommended = OneTimeOffer.BILL_CHECK_PACK,
        footer = null,
        // The one sheet reached without an errand — from a link on the plans behind it — so
        // it offers the way back that the link came from.
        close = Res.string.pw_ot_close,
    ),
    ;

    companion object {

        /**
         * The sheet framing that matches a paywall framing.
         *
         * Only the two walls map to something narrower — the scan one and the export one.
         * Every other trigger sends someone who was reading about the plan in general, and
         * hiding two of three products from them would be guessing.
         */
        fun forTrigger(trigger: PaywallTrigger): OneTimeContext = when (trigger) {
            PaywallTrigger.SCANS_EXHAUSTED -> BILL_CHECK
            PaywallTrigger.RECORD_EXPORT -> EXPORT
            PaywallTrigger.GENERIC, PaywallTrigger.SAVINGS, PaywallTrigger.SMART_REFUEL -> GENERIC
        }

        /**
         * The context named by [value], or [GENERIC].
         *
         * An unrecognised name is not a crash: the key can arrive from a deep link or a
         * saved back stack written by an older build, and the generic framing sells the same
         * things.
         */
        fun of(value: String): OneTimeContext = entries.firstOrNull { it.name == value } ?: GENERIC
    }
}
