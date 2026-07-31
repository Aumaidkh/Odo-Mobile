package com.hopcape.odo.feature.documentvault.navigation

import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentTypeKeyTest {

    @Test
    fun readsTheTypeANavigationKeyNames() {
        assertEquals(DocumentType.PUC, "PUC".toDocumentType())
    }

    @Test
    fun namingNoType_opensWithNothingPreselected() {
        assertNull(null.toDocumentType())
    }

    @Test
    fun anUnknownName_opensWithNothingPreselectedInsteadOfCrashing() {
        // A key serialized by a newer build, restored into this one.
        assertNull("PASSPORT".toDocumentType())
    }
}
