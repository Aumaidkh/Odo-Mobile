package com.hopcape.odo.feature.paywall.presentation.onetime

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.subscription.OneTimeProducts
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import org.jetbrains.compose.resources.StringResource
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_check_pack_subtitle
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_check_pack_title
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_check_single_subtitle
import com.hopcape.odo.feature.paywall.resources.pw_ot_bill_check_single_title
import com.hopcape.odo.feature.paywall.resources.pw_ot_export_subtitle
import com.hopcape.odo.feature.paywall.resources.pw_ot_export_title

/**
 * What Odo sells one at a time, and how each one is described.
 *
 * Declared here rather than assembled in the ViewModel so the sheet's contents are one list
 * to read, and so adding a fourth product is a row rather than a branch. The order is the
 * order they are shown in.
 *
 * The **price is deliberately absent**: it is the store's to state, never the app's.
 */
internal enum class OneTimeOffer(
    val productId: String,
    val title: StringResource,
    val subtitle: StringResource,
    /** Bill checks this grants once bought. Zero for anything that is not a check. */
    val scanCredits: Int = 0,
) {
    BILL_CHECK_SINGLE(
        productId = OneTimeProducts.BILL_CHECK_SINGLE,
        title = Res.string.pw_ot_bill_check_single_title,
        subtitle = Res.string.pw_ot_bill_check_single_subtitle,
        scanCredits = 1,
    ),
    BILL_CHECK_PACK(
        productId = OneTimeProducts.BILL_CHECK_PACK,
        title = Res.string.pw_ot_bill_check_pack_title,
        subtitle = Res.string.pw_ot_bill_check_pack_subtitle,
        scanCredits = 3,
    ),

    /**
     * The record PDF. Its balance is credited where it is spent — the share sheet has sold
     * and granted this since #246 — so buying it here is not wired yet, and the sheet says
     * so rather than taking money it cannot honour.
     */
    RECORD_EXPORT(
        productId = OneTimeProducts.RECORD_EXPORT,
        title = Res.string.pw_ot_export_title,
        subtitle = Res.string.pw_ot_export_subtitle,
    ),
    ;

    /** Whether this sheet can complete the purchase and grant what it sold. */
    val purchasable: Boolean get() = scanCredits > 0
}

/**
 * One product as the sheet draws it.
 *
 * [price] is the store's own formatted string — already localized, already carrying the
 * right symbol. A product with no price never reaches this type at all; see
 * [OneTimeOffersUiState].
 */
@Immutable
internal data class OneTimeOfferCard(
    val offer: OneTimeOffer,
    val price: String,
    /**
     * Drawn as the answer rather than as one of the options.
     *
     * A judgement the context states, not a discount worked out from the prices — the store
     * hands over formatted strings and no amounts, so any "each" or "you save" figure here
     * would be arithmetic on `"₹99"`.
     */
    val recommended: Boolean = false,
)


/**
 * Display state for the one-time offers sheet.
 *
 * The list is [Loadable] because it is read from the store every time the sheet opens, and
 * for the same reason the plans are: a price the app cannot confirm is worse than a retry,
 * because it risks charging someone something they were not shown.
 *
 * **Only products the store actually returns are listed.** A product nobody has created in
 * Play Console yet has no price, and a row with no price is either a lie or a dead end — so
 * it is left out, and the sheet says it has nothing to sell rather than inventing one. That
 * is why this can render empty today: none of these three exist in the store yet.
 */
@Immutable
internal data class OneTimeOffersUiState(
    val context: OneTimeContext = OneTimeContext.GENERIC,
    val offers: Loadable<List<OneTimeOfferCard>> = Loadable.Loading,
    /** The store's sheet is open, or the purchase is completing. Blocks a second tap. */
    val purchasing: Boolean = false,
    /** What the store just said — a refusal, or nothing. Cleared on the next tap. */
    val notice: UiText? = null,
) {
    /** Loaded, but the store returned none of them. */
    val isEmpty: Boolean get() = (offers as? Loadable.Ready)?.value?.isEmpty() == true
}
