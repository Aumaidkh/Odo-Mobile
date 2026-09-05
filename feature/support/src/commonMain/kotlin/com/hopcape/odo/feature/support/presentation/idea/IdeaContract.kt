package com.hopcape.odo.feature.support.presentation.idea

import androidx.compose.runtime.Immutable

/** Where an idea has got to. Set by whoever curates the list, never by the app. */
internal enum class IdeaStatus {
    UNDER_REVIEW,
    IN_PROGRESS,
    SHIPPING,
    SHIPPED,
}

/** One idea somebody already asked for, and whether this owner has added their name to it. */
@Immutable
internal data class IdeaRow(
    val id: String,
    val title: String,
    val status: IdeaStatus,
    val votes: Int,
    val voted: Boolean,
)

@Immutable
internal data class IdeaUiState(
    val text: String = "",
    val ideas: List<IdeaRow> = emptyList(),
    val sending: Boolean = false,
) {
    val canSend: Boolean get() = text.isNotBlank() && !sending
}

internal sealed interface IdeaEvent {

    data object BackClicked : IdeaEvent

    data class TextChanged(val text: String) : IdeaEvent

    /** Tapping a vote adds this owner's name to it, and tapping it again takes it off. */
    data class VoteToggled(val id: String) : IdeaEvent

    data object SendClicked : IdeaEvent
}
