package com.hopcape.odo.feature.servicelog.presentation.share

/** Where a verified record can be shared to. */
internal enum class ShareTarget { WHATSAPP, EMAIL, MORE }

/**
 * The public link to the car's record, and whether it has just been copied.
 *
 * Typed because "no link yet" is the normal state today, not an error: the Resale Passport
 * that issues one is Phase 2. [Unavailable] hides the link row entirely rather than showing
 * an empty field or a placeholder URL that resolves to nothing.
 */
internal sealed interface PassportLinkUiState {
    data object Unavailable : PassportLinkUiState

    data class Ready(
        val url: String,
        /** Flipped for the "Copied" confirmation right after the owner copies it. */
        val copied: Boolean = false,
    ) : PassportLinkUiState
}

/** The record being shared — the resale-passport summary and its link. */
internal data class ShareRecordUiState(
    val content: Content = Content.Loading,
    val link: PassportLinkUiState = PassportLinkUiState.Unavailable,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * [carName] is null when the car couldn't be named — the sheet still shows the
         * counts, which are the part a buyer cares about.
         */
        data class Loaded(
            val carName: String?,
            val verifiedCount: Int,
            val serviceCount: Int,
        ) : Content
    }
}
