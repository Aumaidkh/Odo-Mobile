package com.hopcape.odo.feature.reminders.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.DistanceArg
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderFeed
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.reminder.model.ReminderUrgency
import com.hopcape.odo.core.domain.reminder.model.UpcomingReminder
import com.hopcape.odo.core.domain.reminder.policy.ReminderOccurrence
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.reminders.domain.usecase.CreateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.CustomReminderCommand
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCurrentOdometerUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveRemindersUseCase
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_error_load_failed
import com.hopcape.odo.feature.reminders.resources.rm_line_at_km
import com.hopcape.odo.feature.reminders.resources.rm_line_due_in_days
import com.hopcape.odo.feature.reminders.resources.rm_line_due_in_days_plain
import com.hopcape.odo.feature.reminders.resources.rm_line_due_today
import com.hopcape.odo.feature.reminders.resources.rm_line_due_today_plain
import com.hopcape.odo.feature.reminders.resources.rm_line_service_km
import com.hopcape.odo.feature.reminders.resources.rm_line_service_next
import com.hopcape.odo.feature.reminders.resources.rm_line_service_overdue_days
import com.hopcape.odo.feature.reminders.resources.rm_line_service_overdue_km
import com.hopcape.odo.feature.reminders.resources.rm_line_suggested_every_days
import com.hopcape.odo.feature.reminders.resources.rm_line_suggested_every_km
import com.hopcape.odo.feature.reminders.resources.rm_line_suggested_monthly
import com.hopcape.odo.feature.reminders.resources.rm_line_suggested_once
import com.hopcape.odo.feature.reminders.resources.rm_line_valid_till
import com.hopcape.odo.feature.reminders.resources.rm_new_default_air
import com.hopcape.odo.feature.reminders.resources.rm_new_default_battery
import com.hopcape.odo.feature.reminders.resources.rm_new_default_coolant
import com.hopcape.odo.feature.reminders.resources.rm_new_default_tyre
import com.hopcape.odo.feature.reminders.resources.rm_new_default_wiper
import com.hopcape.odo.feature.reminders.resources.rm_row_insurance
import com.hopcape.odo.feature.reminders.resources.rm_row_licence
import com.hopcape.odo.feature.reminders.resources.rm_row_puc
import com.hopcape.odo.feature.reminders.resources.rm_row_service
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * State holder for the reminders home. Holds [RemindersUiState], consumes
 * [RemindersEvent]s and emits [RemindersEffect]s.
 *
 * The car comes from [ActiveCarProvider] rather than the navigation key: reminders are
 * reached from Home's bell without naming a car, and every per-car surface answering
 * "which car?" for itself is how the app ends up opening someone else's.
 *
 * A one-tap suggestion create that fails leaves the list unchanged — the suggestion row
 * simply stays — and is recorded by telemetry. No copy is shown for it yet.
 */
internal class RemindersViewModel(
    private val activeCar: ActiveCarProvider,
    observeReminders: ObserveRemindersUseCase,
    observeOdometer: ObserveCurrentOdometerUseCase,
    private val createReminder: CreateCustomReminderUseCase,
    private val owners: CurrentOwnerProvider,
    private val telemetry: RemindersTelemetry,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _effects = Channel<RemindersEffect>(Channel.BUFFERED)
    val effects: Flow<RemindersEffect> = _effects.receiveAsFlow()

    /** Guards the opened event so a re-emission does not count a second visit. */
    private var reportedOpen = false

    /** The car's reading today — what a distance suggestion anchors at. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentKm: StateFlow<Int?> = activeCar.activeCarId
        .flatMapLatest { carId -> if (carId == null) flowOf(null) else observeOdometer(carId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), null)

    /**
     * The feed, resolved for today. A failed read becomes [Loadable.Failed] rather than
     * an empty list: telling an owner with a lapsing policy that nothing is due is the
     * worse of the two lies.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RemindersUiState> = activeCar.activeCarId
        .flatMapLatest { carId ->
            // No car yet means setup has not finished; the screen keeps waiting.
            if (carId == null) {
                flowOf(RemindersUiState())
            } else {
                combine(observeReminders(carId), currentKm) { feed, km -> toUiState(feed, km) }
            }
        }
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(RemindersTelemetry.Screen.LIST, cause)
            emit(RemindersUiState(Loadable.Failed(UiText(Res.string.rm_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = RemindersUiState(),
        )

    fun onEvent(event: RemindersEvent) {
        when (event) {
            RemindersEvent.ManageTapped -> emit(RemindersEffect.OpenSettings)
            RemindersEvent.AddTapped -> emit(RemindersEffect.OpenNew)
            is RemindersEvent.ReminderTapped -> openActions(event)
            is RemindersEvent.SuggestionTapped -> createFromSuggestion(event.preset, event.name)
        }
    }

    private fun openActions(event: RemindersEvent.ReminderTapped) {
        val id = event.row.id ?: return
        emit(
            RemindersEffect.OpenActions(
                kind = id.kind.name,
                dueOn = id.dueOn?.toString(),
                customId = id.customId?.value,
                title = event.title,
                due = event.due,
                icon = event.row.icon.name,
            ),
        )
    }

    /**
     * The one-tap opt-in: the preset's defaults, starting today at [DEFAULT_NUDGE_TIME],
     * a distance preset anchored at the car's current reading. The list updates itself —
     * the repository's flow re-emits with the new reminder in it.
     */
    private fun createFromSuggestion(preset: ReminderPreset, name: String) {
        val carId = activeCar.activeCarId.value ?: return
        viewModelScope.launch(telemetry.op(OP_SUGGESTION)) {
            val cadence = preset.defaultCadence
            telemetry.reminderSave(
                cadence = cadence::class.simpleName ?: "",
                preset = preset.name,
                viaSuggestion = true,
                edit = false,
            ) {
                createReminder(
                    command = CustomReminderCommand(
                        title = name,
                        cadence = cadence,
                        startsOn = today(),
                        at = DEFAULT_NUDGE_TIME,
                        preset = preset,
                        anchorKm = currentKm.value,
                    ),
                    carId = carId,
                    ownerId = owners.currentOwnerId(),
                )
            }
        }
    }

    private fun toUiState(feed: ReminderFeed, currentKm: Int?): RemindersUiState {
        val today = today()
        val suggestions = feed.suggestions
            // A distance preset cannot anchor without a reading; offering it would only
            // create a reminder the domain rejects.
            .filter { it.defaultCadence !is ReminderCadence.EveryDistance || currentKm != null }
            .map(::suggestionRow)
        val thisWeek = feed.thisWeek.map { it.toRow(today) }
        return RemindersUiState(
            Loadable.Ready(
                RemindersContent(
                    header = if (thisWeek.isEmpty()) {
                        RemindersHeader.CaughtUp
                    } else {
                        RemindersHeader.Attention(thisWeek.size)
                    },
                    thisWeek = thisWeek,
                    upcoming = feed.upcoming.map { it.toRow(today) } + suggestions,
                ),
            ),
        )
    }

    private fun reportOpened(state: RemindersUiState) {
        val content = (state.content as? Loadable.Ready)?.value ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.opened(
            thisWeek = content.thisWeek.size,
            upcoming = content.upcoming.count { it.status !is RowStatus.Suggested },
            suggestions = content.upcoming.count { it.status is RowStatus.Suggested },
        )
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    private fun emit(effect: RemindersEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val OP_SUGGESTION = "suggestionCreate"

        /** When a one-tap reminder nudges. 9 AM: after the commute starts, before lunch. */
        val DEFAULT_NUDGE_TIME = LocalTime(9, 0)
    }
}

// --- Feed → rows -----------------------------------------------------------------

private fun UpcomingReminder.toRow(today: LocalDate): ReminderRow = when (this) {
    is UpcomingReminder.DocumentRenewal -> ReminderRow(
        id = ReminderRowId(kind, dismissalKey?.dueOn, customId = null),
        icon = if (type == DocumentType.PUC) ReminderIcon.LEAF else ReminderIcon.SHIELD,
        title = RowText.Res(UiText(kind.rowTitle())),
        line = documentLine(),
        status = urgency.toStatus(),
    )

    is UpcomingReminder.ServiceDue -> ReminderRow(
        id = ReminderRowId(kind, dueOn, customId = null),
        icon = ReminderIcon.OIL,
        title = RowText.Res(UiText(Res.string.rm_row_service)),
        line = serviceLine(),
        status = urgency.toStatus(),
    )

    is UpcomingReminder.Custom -> ReminderRow(
        id = ReminderRowId(kind, dismissalKey?.dueOn, customId = reminder.id),
        icon = reminder.preset.icon(),
        title = RowText.Plain(reminder.title.value),
        line = customLine(today),
        status = urgency.toStatus(),
    )
}

private fun ReminderKind.rowTitle() = when (this) {
    ReminderKind.PUC_EXPIRY -> Res.string.rm_row_puc
    ReminderKind.LICENCE_EXPIRY -> Res.string.rm_row_licence
    else -> Res.string.rm_row_insurance
}

private fun UpcomingReminder.DocumentRenewal.documentLine(): UiText = when {
    urgency == ReminderUrgency.ON_TRACK -> UiText(Res.string.rm_line_valid_till, listOf(formatDate(expiresOn)))
    daysLeft == 0 -> UiText(Res.string.rm_line_due_today, listOf(formatDate(expiresOn)))
    else -> UiText(Res.string.rm_line_due_in_days, listOf(daysLeft, formatDate(expiresOn)))
}

private fun UpcomingReminder.ServiceDue.serviceLine(): UiText = when (val s = status) {
    is ServiceDueStatus.Overdue ->
        if (s.daysOverdue == 0 && s.kmOverdue != null) {
            UiText(Res.string.rm_line_service_overdue_km, listOf(DistanceArg(s.kmOverdue!!)))
        } else {
            UiText(Res.string.rm_line_service_overdue_days, listOf(s.daysOverdue))
        }

    is ServiceDueStatus.DueSoon ->
        if (kind == ReminderKind.SERVICE_DUE_KM && s.kmLeft != null) {
            UiText(Res.string.rm_line_service_km, listOf(DistanceArg(s.kmLeft!!)))
        } else {
            UiText(Res.string.rm_line_due_in_days_plain, listOf(s.daysLeft))
        }

    is ServiceDueStatus.NotDue -> UiText(Res.string.rm_line_service_next, listOf(s.daysLeft))

    // The feed never builds a row for a never-serviced car.
    ServiceDueStatus.NeverServiced -> UiText(Res.string.rm_line_service_next, listOf(0))
}

private fun UpcomingReminder.Custom.customLine(today: LocalDate): UiText = when (val occ = occurrence) {
    is ReminderOccurrence.OnDate -> {
        val days = today.daysUntil(occ.date)
        if (days <= 0) {
            UiText(Res.string.rm_line_due_today_plain)
        } else {
            UiText(Res.string.rm_line_due_in_days_plain, listOf(days))
        }
    }

    is ReminderOccurrence.AtOdometer -> UiText(Res.string.rm_line_at_km, listOf(DistanceArg(occ.targetKm)))
}

private fun ReminderUrgency.toStatus(): RowStatus = when (this) {
    ReminderUrgency.DUE_THIS_WEEK -> RowStatus.DueThisWeek
    ReminderUrgency.DUE_SOON -> RowStatus.DueSoon
    ReminderUrgency.ON_TRACK -> RowStatus.OnTrack
}

private fun ReminderPreset?.icon(): ReminderIcon = when (this) {
    ReminderPreset.AIR_PRESSURE, ReminderPreset.TYRE_ROTATION -> ReminderIcon.TYRE
    ReminderPreset.COOLANT, ReminderPreset.WIPER_FLUID, ReminderPreset.BATTERY, null -> ReminderIcon.OIL
}

private fun suggestionRow(preset: ReminderPreset): ReminderRow = ReminderRow(
    id = null,
    icon = preset.icon(),
    title = RowText.Res(UiText(preset.titleRes())),
    line = preset.defaultCadence.suggestionLine(),
    status = RowStatus.Suggested(preset),
)

private fun ReminderPreset.titleRes() = when (this) {
    ReminderPreset.AIR_PRESSURE -> Res.string.rm_new_default_air
    ReminderPreset.COOLANT -> Res.string.rm_new_default_coolant
    ReminderPreset.WIPER_FLUID -> Res.string.rm_new_default_wiper
    ReminderPreset.BATTERY -> Res.string.rm_new_default_battery
    ReminderPreset.TYRE_ROTATION -> Res.string.rm_new_default_tyre
}

private fun ReminderCadence.suggestionLine(): UiText = when (this) {
    is ReminderCadence.EveryDays -> UiText(Res.string.rm_line_suggested_every_days, listOf(days))
    ReminderCadence.Monthly -> UiText(Res.string.rm_line_suggested_monthly)
    is ReminderCadence.EveryDistance -> UiText(Res.string.rm_line_suggested_every_km, listOf(DistanceArg(km)))
    ReminderCadence.Once -> UiText(Res.string.rm_line_suggested_once)
}
