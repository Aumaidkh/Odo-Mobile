package com.hopcape.odo.core.domain.scan.model

import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.LocalDate

/**
 * What an extractor managed to read off a photographed paper — an insurance policy, a PUC
 * certificate, an RC or a licence.
 *
 * The expiry date is the whole point. Without it the vault cannot remind anyone of anything,
 * and typing it by hand is the friction the scanner exists to remove.
 *
 * Deliberately narrow: type, dates and a title suggestion. Insurance detail (provider, policy
 * number, sum insured, cover type) is **not** here. The document vault's plan deferred it,
 * `Document` has nowhere to put it, and storing a policy number would make every share of a
 * document a disclosure decision. It arrives with the schema that can hold it, not before.
 */
data class ExtractedDocument(
    val scanId: ScanId,
    val confidence: ExtractionConfidence,
    /** What the paper appears to be. Null when it could not be told — the owner picks. */
    val documentType: DocumentType? = null,
    /** A suggested label, e.g. the insurer's name as printed. Never a policy number. */
    val suggestedTitle: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
) {
    /**
     * Whether the owner must check the fields before the document is filed.
     *
     * A wrong expiry is worse than no expiry: it produces a reminder for the wrong day and
     * an owner who believes they are covered. So the bar here is the plain confidence floor,
     * with no printed/handwritten distinction to soften it — papers are printed, and a low
     * score on a printed page means the photo was bad.
     */
    val requiresManualReview: Boolean
        get() = confidence.needsManualReview || expiresOn == null

    /** Nothing usable came back. */
    val isEmpty: Boolean get() = documentType == null && expiresOn == null && issuedOn == null
}
