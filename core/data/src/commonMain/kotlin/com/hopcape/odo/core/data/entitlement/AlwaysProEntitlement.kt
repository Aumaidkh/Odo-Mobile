package com.hopcape.odo.core.data.entitlement

import com.hopcape.odo.core.domain.entitlement.ProEntitlement

/**
 * Development stub: everyone is on Pro until something can sell a subscription.
 *
 * **MUST be swapped for a real adapter before launch** — one line in `coreDataModule` — or
 * the paid tier ships free. Razorpay and the entitlement mirror land in M6.
 *
 * It answers *true* rather than false, which is the opposite of what
 * [FreeTierDocumentAllowance][com.hopcape.odo.core.data.document.FreeTierDocumentAllowance]
 * does, and for a reason. The document cap is a limit: answering "free tier" is the honest
 * answer and the owner loses nothing they could have bought. Pro-gated *content* is the
 * other way round — answering false would hide the health-score breakdown behind a paywall
 * that cannot take money yet, so the feature could not be used or demonstrated at all.
 */
internal class AlwaysProEntitlement : ProEntitlement {
    override suspend fun isPro(): Boolean = true
}
