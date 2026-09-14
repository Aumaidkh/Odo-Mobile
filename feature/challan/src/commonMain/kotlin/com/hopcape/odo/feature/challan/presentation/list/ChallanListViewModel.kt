package com.hopcape.odo.feature.challan.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.sum
import com.hopcape.odo.feature.challan.domain.usecase.ChallanOverview
import com.hopcape.odo.feature.challan.domain.usecase.ObserveChallanOverviewUseCase
import com.hopcape.odo.feature.challan.domain.usecase.MarkChallansPaidUseCase
import com.hopcape.odo.feature.challan.domain.usecase.RefreshChallansUseCase
import com.hopcape.odo.feature.challan.presentation.ChallanTelemetry
import com.hopcape.odo.feature.challan.presentation.checkedAgo
import com.hopcape.odo.feature.challan.presentation.formatPlate
import com.hopcape.odo.feature.challan.presentation.state.Loadable
import com.hopcape.odo.feature.challan.presentation.state.valueOrNull
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_clean_body
import com.hopcape.odo.feature.challan.resources.ch_clean_cleared_value
import com.hopcape.odo.feature.challan.resources.ch_error_load_failed
import com.hopcape.odo.feature.challan.resources.ch_error_no_reg_no
import com.hopcape.odo.feature.challan.resources.ch_next_check_day
import com.hopcape.odo.feature.challan.resources.ch_next_check_days
import com.hopcape.odo.feature.challan.resources.ch_next_check_due
import com.hopcape.odo.feature.challan.resources.ch_older_count
import com.hopcape.odo.feature.challan.resources.ch_older_count_one
import com.hopcape.odo.feature.challan.resources.ch_older_penalty_note
import com.hopcape.odo.feature.challan.resources.ch_older_range
import com.hopcape.odo.feature.challan.resources.ch_pay_all_on_parivahan
import com.hopcape.odo.feature.challan.resources.ch_pay_amount_on_parivahan
import com.hopcape.odo.feature.challan.resources.ch_pay_on_parivahan
import com.hopcape.odo.feature.challan.resources.ch_section_payable
import com.hopcape.odo.feature.challan.resources.ch_section_pending
import com.hopcape.odo.feature.challan.resources.ch_section_year
import com.hopcape.odo.feature.challan.resources.ch_section_year_one
import com.hopcape.odo.feature.challan.resources.ch_total_pending_car
import com.hopcape.odo.feature.challan.resources.ch_total_pending_count
import com.hopcape.odo.feature.challan.resources.ch_total_pending_count_one
import com.hopcape.odo.feature.challan.resources.ch_total_pending_one_car
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State holder for the owner's challans. Holds [ChallanListUiState], consumes
 * [ChallanListEvent]s and emits [ChallanListEffect]s.
 *
 * The car comes from [ActiveCarProvider] rather than the navigation key — the same rule
 * as the vault: every per-car surface answering "which car?" for itself is how the app
 * ends up opening someone else's.
 *
 * On the first answer with a stale (or absent) check, a refresh fires by itself: "Odo
 * checks for new challans every week" is this screen keeping the promise on open, since
 * no background job exists yet to keep it while the app sleeps.
 */
internal class ChallanListViewModel(
    activeCar: ActiveCarProvider,
    cars: CarRepository,
    observeOverview: ObserveChallanOverviewUseCase,
    private val refresh: RefreshChallansUseCase,
    private val markPaid: MarkChallansPaidUseCase,
    private val telemetry: ChallanTelemetry,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _effects = Channel<ChallanListEffect>(Channel.BUFFERED)
    val effects: Flow<ChallanListEffect> = _effects.receiveAsFlow()

    private val refreshing = MutableStateFlow(false)
    private val sourceDown = MutableStateFlow(false)
    private val olderExpanded = MutableStateFlow(false)

    /** The plate refreshes run against — remembered from the last content emission. */
    private var currentRegNo: RegistrationNumber? = null

    /** Guards the on-open auto-refresh and the opened event — once per visit. */
    private var autoRefreshed = false
    private var reportedOpen = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChallanListUiState> = activeCar.activeCarId
        .flatMapLatest { carId ->
            if (carId == null) flowOf(null) else cars.observe(carId)
        }
        .map { car -> car?.registrationNumber }
        .distinctUntilChanged()
        .flatMapLatest { regNo ->
            if (regNo == null) {
                flowOf(Loadable.Failed(UiText(Res.string.ch_error_no_reg_no)) as Loadable<ChallanListContent>)
            } else {
                observeOverview(regNo)
                    .onEach { overview -> onOverview(regNo, overview) }
                    .map { overview -> Loadable.Ready(overview.toContent()) as Loadable<ChallanListContent> }
            }
        }
        .combine(refreshing) { content, busy -> ChallanListUiState(content = content, refreshing = busy) }
        .combine(sourceDown) { ui, down -> ui.copy(sourceDown = down) }
        .combine(olderExpanded) { ui, expanded ->
            val ready = ui.content.valueOrNull ?: return@combine ui
            ui.copy(content = Loadable.Ready(ready.copy(olderExpanded = expanded)))
        }
        .catch { cause ->
            telemetry.readFailed(ChallanTelemetry.Screen.LIST, cause)
            emit(ChallanListUiState(content = Loadable.Failed(UiText(Res.string.ch_error_load_failed))))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChallanListUiState())

    fun onEvent(event: ChallanListEvent) {
        when (event) {
            ChallanListEvent.BackTapped -> _effects.trySend(ChallanListEffect.NavigateBack)
            ChallanListEvent.RefreshTapped,
            ChallanListEvent.CheckAgainTapped,
            ChallanListEvent.TryAgainTapped -> refreshNow()

            ChallanListEvent.PayTapped -> {
                telemetry.payTapped(pendingCount())
                _effects.trySend(ChallanListEffect.OpenParivahan(PARIVAHAN_URL))
            }

            ChallanListEvent.AlreadyPaidTapped -> claimPaid()
            ChallanListEvent.OlderToggled -> olderExpanded.value = !olderExpanded.value
            ChallanListEvent.OpenParivahanTapped ->
                _effects.trySend(ChallanListEffect.OpenParivahan(PARIVAHAN_URL))
        }
    }

    private fun onOverview(regNo: RegistrationNumber, overview: ChallanOverview) {
        currentRegNo = regNo
        if (!reportedOpen) {
            reportedOpen = true
            telemetry.listOpened(pendingCount = overview.payable.size, courtCount = overview.courtCases.size)
        }
        // The weekly promise, kept on open. Once per visit: a stale answer that stays
        // stale because the source is down must not retry in a loop.
        if (!autoRefreshed && refresh.isStale(overview.lastCheckedAt)) {
            autoRefreshed = true
            refreshNow()
        }
    }

    private fun refreshNow() {
        val regNo = currentRegNo ?: return
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = refresh(regNo)
            refreshing.value = false
            sourceDown.value = result.leftOrNull() is DomainError.ChallanRecordsUnreachable
            telemetry.refreshed(succeeded = result.isRight())
        }
    }

    private fun claimPaid() {
        val regNo = currentRegNo ?: return
        viewModelScope.launch {
            telemetry.markedPaid(pendingCount())
            markPaid(regNo)
        }
    }

    private fun pendingCount(): Int = state.value.content.valueOrNull
        ?.sections?.sumOf { it.rows.size } ?: 0

    private fun ChallanOverview.toContent(): ChallanListContent {
        val now = clock.now()
        val checked = lastCheckedAt?.let { checkedAgo(it, now) }
        return ChallanListContent(
            regNo = formatPlate(regNo.value),
            checkedAgo = checked,
            totalPending = totalPendingCard(),
            courtCases = courtCases.map { challan ->
                CourtCaseRow(
                    id = challan.id.value,
                    violation = challan.violation,
                    number = challan.id.value,
                    amount = challan.amount.formatRupees(),
                    courtName = challan.courtName ?: challan.location,
                    nextHearing = challan.nextHearingOn?.let(::formatDate),
                )
            },
            sections = sections(),
            older = olderBucket(),
            clean = cleanStats(),
            pay = payCta(),
            offerAlreadyPaid = payable.isNotEmpty() && courtCases.isEmpty() && !spansYears(),
        )
    }

    /** Whether the payable challans reach beyond the two most recent years present. */
    private fun ChallanOverview.spansYears(): Boolean = payable.map { it.issuedOn.year }.distinct().size > 1

    private fun ChallanOverview.recentYears(): List<Int> =
        payable.map { it.issuedOn.year }.distinct().sortedDescending().take(RECENT_YEARS)

    private fun ChallanOverview.olderChallans() =
        payable.filter { it.issuedOn.year !in recentYears() }

    private fun ChallanOverview.totalPendingCard(): TotalPendingCard? {
        if (payable.isEmpty()) return null
        val countLine = when {
            !spansYears() && payable.size == 1 ->
                UiText(Res.string.ch_total_pending_one_car, listOf(payable.size, formatPlate(regNo.value)))

            !spansYears() ->
                UiText(Res.string.ch_total_pending_car, listOf(payable.size, formatPlate(regNo.value)))

            payable.size == 1 -> UiText(Res.string.ch_total_pending_count_one, listOf(payable.size))
            else -> UiText(Res.string.ch_total_pending_count, listOf(payable.size))
        }
        return TotalPendingCard(
            amount = payableTotal.formatRupees(),
            countLine = countLine,
            segments = if (spansYears()) yearSegments() else emptyList(),
        )
    }

    private fun ChallanOverview.yearSegments(): List<YearSegment> {
        val total = payableTotal.paise.toFloat().coerceAtLeast(1f)
        val recent = recentYears().map { year ->
            val inYear = payable.filter { it.issuedOn.year == year }
            YearSegment(
                label = year.toString(),
                amount = inYear.map { it.amount }.sum().formatRupees(),
                fraction = inYear.sumOf { it.amount.paise }.toFloat() / total,
            )
        }
        val older = olderChallans()
        if (older.isEmpty()) return recent
        return recent + YearSegment(
            label = OLDER_LABEL,
            amount = older.map { it.amount }.sum().formatRupees(),
            fraction = older.sumOf { it.amount.paise }.toFloat() / total,
        )
    }

    private fun ChallanOverview.sections(): List<ChallanSection> {
        if (payable.isEmpty()) return emptyList()
        if (!spansYears()) {
            // One year: the flat section — titled by what it means here ("Pending", or
            // "Payable online · Rs. X" when a court case sits above and the distinction
            // is the whole point).
            val title = if (courtCases.isEmpty()) {
                UiText(Res.string.ch_section_pending)
            } else {
                UiText(Res.string.ch_section_payable, listOf(payableTotal.formatRupees()))
            }
            return listOf(ChallanSection(title = title, rows = payable.map { it.toRow() }, compact = false))
        }
        return recentYears().map { year ->
            val inYear = payable.filter { it.issuedOn.year == year }
            ChallanSection(
                title = UiText(
                    if (inYear.size == 1) Res.string.ch_section_year_one else Res.string.ch_section_year,
                    listOf(year.toString(), inYear.size),
                ),
                rows = inYear.map { it.toRow() },
                compact = true,
            )
        }
    }

    private fun ChallanOverview.olderBucket(): OlderBucket? {
        val older = olderChallans().takeIf { it.isNotEmpty() } ?: return null
        val years = older.map { it.issuedOn.year }
        return OlderBucket(
            countLine = UiText(
                if (older.size == 1) Res.string.ch_older_count_one else Res.string.ch_older_count,
                listOf(older.size),
            ),
            rangeLine = UiText(Res.string.ch_older_range, listOf(years.min().toString(), years.max().toString())),
            amount = older.map { it.amount }.sum().formatRupees(),
            rows = older.map { it.toRow() },
        )
    }

    private fun ChallanOverview.cleanStats(): CleanStats? {
        if (!isClean) return null
        val now = clock.now()
        val days = refresh.daysUntilNextCheck(lastCheckedAt)
        return CleanStats(
            body = UiText(Res.string.ch_clean_body, listOf(formatPlate(regNo.value))),
            lastChecked = lastCheckedAt?.let { checkedAgo(it, now) },
            clearedThisYear = clearedThisYearCount.takeIf { it > 0 }?.let {
                UiText(Res.string.ch_clean_cleared_value, listOf(it, clearedThisYearTotal.formatRupees()))
            },
            nextCheck = when {
                days <= 0 -> UiText(Res.string.ch_next_check_due)
                days == 1 -> UiText(Res.string.ch_next_check_day, listOf(days))
                else -> UiText(Res.string.ch_next_check_days, listOf(days))
            },
        )
    }

    private fun ChallanOverview.payCta(): PayCta? {
        if (payable.isEmpty()) return null
        return when {
            spansYears() -> PayCta(
                label = UiText(Res.string.ch_pay_all_on_parivahan),
                caption = UiText(Res.string.ch_older_penalty_note),
            )

            courtCases.isNotEmpty() -> PayCta(
                label = UiText(Res.string.ch_pay_amount_on_parivahan, listOf(payableTotal.formatRupees())),
                caption = null,
            )

            else -> PayCta(label = UiText(Res.string.ch_pay_on_parivahan), caption = null)
        }
    }

    private fun Challan.toRow() = ChallanRow(
        id = id.value,
        violation = violation,
        number = id.value,
        amount = amount.formatRupees(),
        location = location,
        date = formatDayMonth(issuedOn),
    )

    companion object {
        /** The official e-challan portal — where every payment actually happens. */
        const val PARIVAHAN_URL = "https://echallan.parivahan.gov.in/"

        /** How many most-recent years get their own section before "Older" collapses. */
        private const val RECENT_YEARS = 2

        /** The hero bar's third segment. Copy, but locale-stable like the year labels beside it. */
        private const val OLDER_LABEL = "Older"
    }
}
