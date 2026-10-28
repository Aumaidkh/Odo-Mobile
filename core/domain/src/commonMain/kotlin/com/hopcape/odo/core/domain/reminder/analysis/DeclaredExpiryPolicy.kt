package com.hopcape.odo.core.domain.reminder.analysis

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import com.hopcape.odo.core.domain.reminder.model.ReminderKind

/** Which declared expiries still apply. A filed paper of the same kind with an expiry wins. */
object DeclaredExpiryPolicy {

    fun applicable(declared: List<DeclaredExpiry>, documents: List<Document>): List<DeclaredExpiry> {
        val filed = documents
            .filter { it.expiresOn != null }
            .mapNotNull { ReminderKind.forDocument(it.type) }
            .toSet()
        return declared.filter { it.kind !in filed }
    }

    /** The paper a declared kind stands in for, or `null` for kinds no paper renews. */
    fun documentTypeFor(kind: ReminderKind): DocumentType? = when (kind) {
        ReminderKind.INSURANCE_EXPIRY -> DocumentType.INSURANCE
        ReminderKind.PUC_EXPIRY -> DocumentType.PUC
        ReminderKind.LICENCE_EXPIRY -> DocumentType.LICENCE
        else -> null
    }
}
