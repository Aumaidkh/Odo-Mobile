package com.hopcape.odo.feature.documentvault.presentation.add

/** The document type being added — the tracked four plus a free-form "Other". */
internal enum class AddDocKind { INSURANCE, PUC, RC, LICENCE, OTHER }

/**
 * Display state for the "Add document" screen — the type picker; the capture method is
 * an action, not a selection, so it isn't held here. [selectedKind] can be pre-filled
 * from the vault row the owner tapped "Add" on.
 */
internal data class AddDocumentUiState(
    val selectedKind: AddDocKind = AddDocKind.INSURANCE,
)
