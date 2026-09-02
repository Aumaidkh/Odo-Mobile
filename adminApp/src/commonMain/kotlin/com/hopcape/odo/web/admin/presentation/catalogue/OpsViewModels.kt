package com.hopcape.odo.web.admin.presentation.catalogue

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.BillingRepository
import com.hopcape.odo.web.admin.domain.BillingSummary
import com.hopcape.odo.web.admin.domain.CatalogueRepository
import com.hopcape.odo.web.admin.domain.ServiceItem
import com.hopcape.odo.web.admin.domain.Subscription
import com.hopcape.odo.web.admin.domain.Ticket
import com.hopcape.odo.web.admin.domain.TicketsRepository
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.readAll
import com.hopcape.odo.web.admin.presentation.readInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_ops_saved
import com.hopcape.odo.web.admin.ui.component.Page
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.textField
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Service catalogue ────────────────────────────────────────────────────────

sealed interface CatalogueEvent {
    data object Refresh : CatalogueEvent
    data class SearchChanged(val value: String) : CatalogueEvent
    data class EditRequested(val item: ServiceItem) : CatalogueEvent
    data object EditorDismissed : CatalogueEvent
    data class NameChanged(val value: String) : CatalogueEvent
    data class KmChanged(val value: String) : CatalogueEvent
    data class MonthsChanged(val value: String) : CatalogueEvent
    data class RupeesChanged(val value: String) : CatalogueEvent
    data class NotesChanged(val value: String) : CatalogueEvent
    data object EditorSubmitted : CatalogueEvent
    data class ActiveToggled(val item: ServiceItem) : CatalogueEvent
    data object NextPage : CatalogueEvent
    data object PreviousPage : CatalogueEvent
    data object MessageDismissed : CatalogueEvent
}

/**
 * The edit form.
 *
 * Every number is a string here, and that is deliberate: a field bound to an `Int`
 * cannot hold "" or "1o", so it either refuses the keystroke or silently keeps the
 * old value. Parsing happens once, on submit, where a bad value can be reported.
 */
@Immutable
data class ItemEditor(
    val id: String,
    val name: FormField<String>,
    val km: FormField<String>,
    val months: FormField<String>,
    val rupees: FormField<String>,
    val notes: FormField<String>,
) {
    val canSubmit: Boolean get() = name.value.isNotBlank()
}

@Immutable
data class CatalogueUiState(
    val items: Loadable<List<ServiceItem>> = Loadable.Loading,
    val search: String = "",
    val page: Page = Page(0),
    val editor: ItemEditor? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val matching: List<ServiceItem>
        get() {
            val term = search.trim()
            if (term.isEmpty()) return items.valueOrNull.orEmpty()
            return items.valueOrNull.orEmpty().filter {
                it.name.contains(term, ignoreCase = true) || it.slug.contains(term, ignoreCase = true)
            }
        }

    val visible: List<ServiceItem> get() = page.windowOf(matching)
}

class CatalogueViewModel(private val catalogue: CatalogueRepository) : ViewModel() {

    private val _state = MutableStateFlow(CatalogueUiState())
    val state: StateFlow<CatalogueUiState> = _state.asStateFlow()

    private val items = MutableStateFlow<Loadable<List<ServiceItem>>>(Loadable.Loading)

    init {
        viewModelScope.launch { items.collect { v -> _state.value = _state.value.copy(items = v) } }
        load()
    }

    fun onEvent(event: CatalogueEvent) {
        when (event) {
            CatalogueEvent.Refresh -> load()
            is CatalogueEvent.SearchChanged ->
                _state.value = _state.value.copy(search = event.value, page = _state.value.page.reset())

            is CatalogueEvent.EditRequested -> _state.value = _state.value.copy(
                editor = ItemEditor(
                    id = event.item.id,
                    name = textField(event.item.name),
                    km = textField(event.item.intervalKm?.toString().orEmpty()),
                    months = textField(event.item.intervalMonths?.toString().orEmpty()),
                    // Rupees in the form, paise in the database. Nobody types paise.
                    rupees = textField(event.item.benchmarkRupees?.toString().orEmpty()),
                    notes = textField(event.item.notes.orEmpty()),
                ),
            )

            CatalogueEvent.EditorDismissed -> _state.value = _state.value.copy(editor = null)
            is CatalogueEvent.NameChanged -> edit { copy(name = name.update(event.value)) }
            is CatalogueEvent.KmChanged -> edit { copy(km = km.update(event.value.digits())) }
            is CatalogueEvent.MonthsChanged -> edit { copy(months = months.update(event.value.digits())) }
            is CatalogueEvent.RupeesChanged -> edit { copy(rupees = rupees.update(event.value.digits())) }
            is CatalogueEvent.NotesChanged -> edit { copy(notes = notes.update(event.value)) }

            CatalogueEvent.EditorSubmitted -> submit()

            is CatalogueEvent.ActiveToggled -> write { catalogue.setActive(event.item.id, !event.item.isActive) }

            CatalogueEvent.NextPage -> if (_state.value.page.hasNext(_state.value.matching.size)) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index + 1))
            }
            CatalogueEvent.PreviousPage -> if (_state.value.page.hasPrevious) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index - 1))
            }
            CatalogueEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    /** Digits only. Cheaper than validating a free-text number after the fact. */
    private fun String.digits(): String = filter { it.isDigit() }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(items) { catalogue.items() } },
    )

    private fun edit(block: ItemEditor.() -> ItemEditor) {
        _state.value = _state.value.copy(editor = _state.value.editor?.block())
    }

    private fun submit() {
        val editor = _state.value.editor ?: return
        if (!editor.canSubmit) return
        write(closeEditor = true) {
            catalogue.save(
                id = editor.id,
                name = editor.name.value.trim(),
                intervalKm = editor.km.value.toIntOrNull(),
                intervalMonths = editor.months.value.toIntOrNull(),
                // Rupees in, paise stored — one multiplication in one place.
                benchmarkPaise = editor.rupees.value.toLongOrNull()?.times(100),
                notes = editor.notes.value.trim().ifBlank { null },
            )
        }
    }

    private fun write(closeEditor: Boolean = false, action: suspend () -> Either<WebError, Unit>) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { e -> _state.value = _state.value.copy(busy = false, message = e.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(
                        busy = false,
                        message = UiText.Resource(Res.string.ad_ops_saved),
                        editor = if (closeEditor) null else _state.value.editor,
                    )
                    load()
                },
            )
        }
    }
}

// ── Support tickets ──────────────────────────────────────────────────────────

sealed interface TicketsEvent {
    data object Refresh : TicketsEvent
    data class SearchChanged(val value: String) : TicketsEvent
    data class StatusChanged(val ticket: Ticket, val status: String) : TicketsEvent
    data class PriorityChanged(val ticket: Ticket, val priority: String) : TicketsEvent
    data class Opened(val id: Long?) : TicketsEvent
    data object NextPage : TicketsEvent
    data object PreviousPage : TicketsEvent
    data object MessageDismissed : TicketsEvent
}

@Immutable
data class TicketsUiState(
    val tickets: Loadable<List<Ticket>> = Loadable.Loading,
    val search: String = "",
    val page: Page = Page(0),
    /** The ticket whose body is expanded. One at a time; the list is the point. */
    val openId: Long? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val matching: List<Ticket>
        get() {
            val term = search.trim()
            if (term.isEmpty()) return tickets.valueOrNull.orEmpty()
            return tickets.valueOrNull.orEmpty().filter {
                it.subject.contains(term, ignoreCase = true) ||
                    it.contact.contains(term, ignoreCase = true) ||
                    it.status.contains(term, ignoreCase = true)
            }
        }

    val visible: List<Ticket> get() = page.windowOf(matching)
    val openCount: Int get() = tickets.valueOrNull.orEmpty().count { it.isOpen }
}

class TicketsViewModel(private val tickets: TicketsRepository) : ViewModel() {

    private val _state = MutableStateFlow(TicketsUiState())
    val state: StateFlow<TicketsUiState> = _state.asStateFlow()

    private val rows = MutableStateFlow<Loadable<List<Ticket>>>(Loadable.Loading)

    init {
        viewModelScope.launch { rows.collect { v -> _state.value = _state.value.copy(tickets = v) } }
        load()
    }

    fun onEvent(event: TicketsEvent) {
        when (event) {
            TicketsEvent.Refresh -> load()
            is TicketsEvent.SearchChanged ->
                _state.value = _state.value.copy(search = event.value, page = _state.value.page.reset())

            is TicketsEvent.Opened ->
                _state.value = _state.value.copy(openId = event.id.takeIf { it != _state.value.openId })

            is TicketsEvent.StatusChanged -> write { tickets.setStatus(event.ticket.id, event.status) }
            is TicketsEvent.PriorityChanged -> write { tickets.setPriority(event.ticket.id, event.priority) }

            TicketsEvent.NextPage -> if (_state.value.page.hasNext(_state.value.matching.size)) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index + 1))
            }
            TicketsEvent.PreviousPage -> if (_state.value.page.hasPrevious) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index - 1))
            }
            TicketsEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(rows) { tickets.tickets() } },
    )

    private fun write(action: suspend () -> Either<WebError, Unit>) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { e -> _state.value = _state.value.copy(busy = false, message = e.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(busy = false, message = UiText.Resource(Res.string.ad_ops_saved))
                    load()
                },
            )
        }
    }
}

// ── Billing ──────────────────────────────────────────────────────────────────

sealed interface BillingEvent {
    data object Refresh : BillingEvent
    data class SearchChanged(val value: String) : BillingEvent
    data object NextPage : BillingEvent
    data object PreviousPage : BillingEvent
}

@Immutable
data class BillingUiState(
    val subscriptions: Loadable<List<Subscription>> = Loadable.Loading,
    val summary: BillingSummary? = null,
    val search: String = "",
    val page: Page = Page(0),
    /** A read is in flight. Read-only screen, so this is only ever a reload. */
    val busy: Boolean = false,
) {
    val matching: List<Subscription>
        get() {
            val term = search.trim()
            if (term.isEmpty()) return subscriptions.valueOrNull.orEmpty()
            return subscriptions.valueOrNull.orEmpty().filter {
                it.ownerContact?.contains(term, ignoreCase = true) == true ||
                    it.tier.contains(term, ignoreCase = true) ||
                    it.status.contains(term, ignoreCase = true)
            }
        }

    val visible: List<Subscription> get() = page.windowOf(matching)
}

/** Read-only. Nothing here writes, because a subscription's truth is the store's. */
class BillingViewModel(private val billing: BillingRepository) : ViewModel() {

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val rows = MutableStateFlow<Loadable<List<Subscription>>>(Loadable.Loading)

    init {
        viewModelScope.launch { rows.collect { v -> _state.value = _state.value.copy(subscriptions = v) } }
        load()
    }

    fun onEvent(event: BillingEvent) {
        when (event) {
            BillingEvent.Refresh -> load()
            is BillingEvent.SearchChanged ->
                _state.value = _state.value.copy(search = event.value, page = _state.value.page.reset())

            BillingEvent.NextPage -> if (_state.value.page.hasNext(_state.value.matching.size)) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index + 1))
            }
            BillingEvent.PreviousPage -> if (_state.value.page.hasPrevious) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index - 1))
            }
        }
    }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(rows) { billing.subscriptions() } },
        // Summed in the database, not over the page: adding up one screenful and
        // calling it the month's total is the classic dashboard lie.
        { billing.summary().onRight { _state.value = _state.value.copy(summary = it) } },
    )
}
