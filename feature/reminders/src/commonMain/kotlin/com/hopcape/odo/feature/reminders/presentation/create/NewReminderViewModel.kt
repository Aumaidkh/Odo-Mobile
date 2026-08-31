package com.hopcape.odo.feature.reminders.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.reminder.model.ReminderTitle
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.reminders.domain.usecase.CreateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.CustomReminderCommand
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCurrentOdometerUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.presentation.RemindersTelemetry
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_new_error_name_blank
import com.hopcape.odo.feature.reminders.resources.rm_new_error_name_long
import com.hopcape.odo.feature.reminders.resources.rm_new_error_save
import com.hopcape.odo.feature.reminders.resources.rm_new_error_start_past
import com.hopcape.odo.feature.reminders.resources.rm_new_error_step_not_positive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * State holder for the create/edit form. Holds [NewReminderUiState], consumes
 * [NewReminderEvent]s and emits [NewReminderEffect]s.
 *
 * [reminderId] switches the mode: `null` creates, a value prefills that reminder for
 * editing. An edit re-runs the same validation a create does, and — for a distance
 * cadence — re-anchors at today's odometer reading, which is what rescheduling a
 * by-distance reminder means.
 *
 * Cadences the form cannot express (an every-20-days written by a future build) prefill
 * as their nearest chip; saving the edit stores what the chip says.
 */
internal class NewReminderViewModel(
    args: NewReminderArgs,
    private val activeCar: ActiveCarProvider,
    private val owners: CurrentOwnerProvider,
    observeOdometer: ObserveCurrentOdometerUseCase,
    observeReminder: ObserveCustomReminderUseCase,
    private val createReminder: CreateCustomReminderUseCase,
    private val updateReminder: UpdateCustomReminderUseCase,
    private val telemetry: RemindersTelemetry,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val reminderId: ReminderId? = args.reminderId?.let(::ReminderId)

    private val _effects = Channel<NewReminderEffect>(Channel.BUFFERED)
    val effects: Flow<NewReminderEffect> = _effects.receiveAsFlow()

    private val _state = MutableStateFlow(
        NewReminderUiState(
            editing = reminderId != null,
            startMillis = todayUtcMillis(),
        ).let { initial ->
            // A suggestion row's tap, not "Remind me": pre-fill what one-tap create would
            // have used, so this reads as "here's what that would make, tweak if you want"
            // rather than a blank form. An edit's own prefill (below) always wins.
            val preset = args.suggestedPreset?.let { name -> ReminderPreset.entries.find { it.name == name } }
            if (reminderId == null && preset != null) {
                initial.prefilledFromSuggestion(preset, args.suggestedName.orEmpty())
            } else {
                initial
            }
        },
    )
    val state: StateFlow<NewReminderUiState> = _state.asStateFlow()

    init {
        watchOdometer(observeOdometer)
        if (reminderId != null) prefill(observeReminder, reminderId)
    }

    fun onEvent(event: NewReminderEvent) {
        when (event) {
            is NewReminderEvent.PresetSelected -> _state.update {
                val presetStep = (event.preset.defaultCadence as? ReminderCadence.EveryDistance)?.km
                it.copy(
                    preset = event.preset,
                    name = event.defaultName,
                    nameError = null,
                    // Only a preset that is itself distance-based has an opinion on the
                    // step; picking "Coolant" must not silently overwrite a step the owner
                    // already typed for a by-distance reminder they are still building.
                    distanceStepKm = presetStep ?: it.distanceStepKm,
                )
            }

            is NewReminderEvent.CustomLabelSaved -> _state.update {
                it.copy(preset = null, customLabel = event.label, name = event.label, nameError = null)
            }

            is NewReminderEvent.NameChanged -> _state.update { it.copy(name = event.name, nameError = null) }

            is NewReminderEvent.RepeatChanged -> _state.update {
                // The disabled chip cannot be tapped, but a race with a vanishing reading
                // is still possible; refusing here keeps the invariant in one place.
                if (event.repeat == ReminderRepeat.BY_DISTANCE && !it.distanceAvailable) it
                else it.copy(repeat = event.repeat)
            }

            is NewReminderEvent.StartChanged -> _state.update {
                it.copy(startMillis = event.millis, startError = null)
            }

            is NewReminderEvent.TimeChanged -> _state.update {
                it.copy(hour = event.hour, minute = event.minute)
            }

            is NewReminderEvent.DistanceStepChanged -> _state.update {
                it.copy(distanceStepKm = event.km, distanceStepError = null)
            }

            NewReminderEvent.ChangeChannelsTapped -> _effects.trySend(NewReminderEffect.OpenSettings)
            NewReminderEvent.SaveTapped -> save()
            NewReminderEvent.CloseTapped -> _effects.trySend(NewReminderEffect.Close)
        }
    }

    private fun watchOdometer(observeOdometer: ObserveCurrentOdometerUseCase) {
        @OptIn(ExperimentalCoroutinesApi::class)
        activeCar.activeCarId
            .flatMapLatest { carId -> if (carId == null) flowOf(null) else observeOdometer(carId) }
            .onEach { km ->
                _state.update { current ->
                    current.copy(
                        anchorKm = km,
                        // The reading disappearing from under a picked by-distance chip
                        // falls back to the default rather than saving an anchorless cadence.
                        repeat = if (km == null && current.repeat == ReminderRepeat.BY_DISTANCE) {
                            ReminderRepeat.EVERY_15_DAYS
                        } else {
                            current.repeat
                        },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun prefill(observeReminder: ObserveCustomReminderUseCase, id: ReminderId) {
        viewModelScope.launch(telemetry.op(OP_PREFILL)) {
            val reminder = observeReminder(id).first()
            if (reminder == null) {
                // Edited from a stale list entry; there is nothing to edit.
                _effects.trySend(NewReminderEffect.Close)
                return@launch
            }
            _state.update { it.prefilledFrom(reminder) }
        }
    }

    private fun save() {
        val snapshot = _state.value
        if (snapshot.saving) return
        val carId = activeCar.activeCarId.value ?: return
        _state.update {
            it.copy(saving = true, nameError = null, startError = null, distanceStepError = null, formError = null)
        }

        val cadence = snapshot.repeat.toCadence()
        val command = CustomReminderCommand(
            title = snapshot.name,
            cadence = cadence,
            startsOn = LocalDate.fromEpochDays((snapshot.startMillis / MILLIS_PER_DAY).toInt()),
            at = LocalTime(snapshot.hour, snapshot.minute),
            preset = snapshot.preset,
            anchorKm = snapshot.anchorKm,
        )

        viewModelScope.launch(telemetry.op(OP_SAVE)) {
            telemetry.reminderSave(
                cadence = cadence::class.simpleName ?: "",
                preset = snapshot.preset?.name,
                viaSuggestion = false,
                edit = reminderId != null,
            ) {
                if (reminderId != null) updateReminder(reminderId, command)
                else createReminder(command, carId, owners.currentOwnerId())
            }.fold(
                ifLeft = { errors -> _state.update { it.withErrors(errors) } },
                ifRight = { _effects.trySend(NewReminderEffect.Close) },
            )
        }
    }

    private fun todayUtcMillis(): Long {
        val today = clock.now().toLocalDateTime(timeZone).date
        return today.toEpochDays() * MILLIS_PER_DAY
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val OP_SAVE = "save"
        const val OP_PREFILL = "prefill"
    }

    private fun ReminderRepeat.toCadence(): ReminderCadence = when (this) {
        ReminderRepeat.EVERY_15_DAYS -> ReminderCadence.EveryDays(15)
        ReminderRepeat.MONTHLY -> ReminderCadence.Monthly
        ReminderRepeat.ONCE -> ReminderCadence.Once
        ReminderRepeat.BY_DISTANCE -> ReminderCadence.EveryDistance(_state.value.distanceStepKm)
    }
}

/** Field failures land on their fields; anything else becomes the form-level line. */
private fun NewReminderUiState.withErrors(errors: NonEmptyList<DomainError>): NewReminderUiState {
    var next = copy(saving = false)
    errors.forEach { error ->
        next = when (error) {
            DomainError.BlankReminderTitle ->
                next.copy(nameError = UiText(Res.string.rm_new_error_name_blank))

            is DomainError.ReminderTitleTooLong ->
                next.copy(nameError = UiText(Res.string.rm_new_error_name_long, listOf(ReminderTitle.MAX_LENGTH)))

            DomainError.ReminderStartInPast ->
                next.copy(startError = UiText(Res.string.rm_new_error_start_past))

            DomainError.ReminderIntervalNotPositive ->
                next.copy(distanceStepError = UiText(Res.string.rm_new_error_step_not_positive))

            else -> next.copy(formError = UiText(Res.string.rm_new_error_save))
        }
    }
    return next
}

/** A stored reminder back into form fields, its cadence mapped to the nearest chip. */
private fun NewReminderUiState.prefilledFrom(reminder: CustomReminder): NewReminderUiState = copy(
    preset = reminder.preset,
    customLabel = if (reminder.preset == null) reminder.title.value else customLabel,
    name = reminder.title.value,
    repeat = reminder.cadence.toRepeat(),
    distanceStepKm = (reminder.cadence as? ReminderCadence.EveryDistance)?.km ?: distanceStepKm,
    startMillis = reminder.startsOn.toEpochDays() * 86_400_000L,
    hour = reminder.at.hour,
    minute = reminder.at.minute,
)

/**
 * A tapped suggestion's preset, pre-filling exactly what one-tap "Remind me" would have
 * created — [RemindersViewModel.createFromSuggestion]'s own defaults — so the form reads
 * as that reminder, ready to adjust before it's saved for real.
 */
private fun NewReminderUiState.prefilledFromSuggestion(preset: ReminderPreset, name: String): NewReminderUiState = copy(
    preset = preset,
    name = name,
    repeat = preset.defaultCadence.toRepeat(),
    distanceStepKm = (preset.defaultCadence as? ReminderCadence.EveryDistance)?.km ?: distanceStepKm,
)

private fun ReminderCadence.toRepeat(): ReminderRepeat = when (this) {
    is ReminderCadence.EveryDays -> ReminderRepeat.EVERY_15_DAYS
    ReminderCadence.Monthly -> ReminderRepeat.MONTHLY
    ReminderCadence.Once -> ReminderRepeat.ONCE
    is ReminderCadence.EveryDistance -> ReminderRepeat.BY_DISTANCE
}
