package com.hopcape.odo.feature.servicelog.presentation.state

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry

/**
 * The "what was done" line every surface shows under a service (a card's footer, the detail
 * header, the report's context strip).
 *
 * Typed rather than a `String?`, because the two sources of that line are not the same kind
 * of text: [Described] is what the owner typed (their line items or their note) and travels
 * as-is, while [Tagged] is a set of domain categories whose *copy* lives in `strings.xml`.
 * A ViewModel that assembled either into one string would either drop the owner's wording or
 * hardcode UI copy — so it resolves in the UI ([asString]) and the state stays framework-free.
 */
internal sealed interface WorkDone {

    /** Nothing was itemised, noted or tagged — the line is simply absent. */
    data object Unspecified : WorkDone

    /** The owner's own words: the priced lines' labels, or the entry's note. */
    data class Described(val labels: List<String>) : WorkDone

    /** Only "what was done" tags — the UI renders each category's localized label. */
    data class Tagged(val categories: List<ServiceCategory>) : WorkDone
}

/**
 * The entry's work-done line, most specific source first: the priced lines name the work
 * exactly, a note names it in the owner's words, and the tags are the coarse fallback.
 */
internal fun ServiceLogEntry.workDone(): WorkDone {
    val itemLabels = lineItems.mapNotNull { it.label }
    val note = notes?.value
    return when {
        itemLabels.isNotEmpty() -> WorkDone.Described(itemLabels)
        note != null -> WorkDone.Described(listOf(note))
        categories.isNotEmpty() -> WorkDone.Tagged(categories.sorted())
        else -> WorkDone.Unspecified
    }
}
