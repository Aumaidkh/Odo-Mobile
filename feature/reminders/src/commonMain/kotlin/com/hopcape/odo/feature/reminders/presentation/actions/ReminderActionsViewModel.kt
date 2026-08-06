package com.hopcape.odo.feature.reminders.presentation.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.right
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.feature.reminders.domain.usecase.DismissReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.SetReminderPausedUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.presentation.RemindersTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * State holder for the actions sheet. The reminder's identity arrives from the
 * navigation key as primitives, parsed here once.
 *
 * What each action means depends on the source:
 *  - **Snooze** dismisses this occurrence for both sources. Hidden when there is no
 *    dated occurrence to dismiss (a distance target).
 *  - **Turn off** pauses a custom reminder; for a derived kind it flips the matching
 *    topic preference, because no per-reminder mute exists — turning off "insurance
 *    renewal" *is* turning off document-expiry reminders.
 *  - **Reschedule** opens the custom reminder's edit form. Hidden for derived kinds,
 *    whose dates come from documents and service history, not from a schedule anyone
 *    can move.
 *
 * The sheet closes after an attempted action either way; a failure is recorded by
 * telemetry. No copy is shown for it yet.
 */
internal class ReminderActionsViewModel(
    args: ReminderActionsArgs,
    private val activeCar: ActiveCarProvider,
    private val dismissReminder: DismissReminderUseCase,
    private val setPaused: SetReminderPausedUseCase,
    private val observeSettings: ObserveReminderSettingsUseCase,
    private val updateSettings: UpdateReminderSettingsUseCase,
    private val telemetry: RemindersTelemetry,
) : ViewModel() {

    private val kind: ReminderKind? = ReminderKind.entries.firstOrNull { it.name == args.kind }
    private val dueOn: LocalDate? = args.dueOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private val customId: ReminderId? = args.customId?.let(::ReminderId)

    /** Whether the sheet shows each row — decided from identity, not from copy. */
    val canSnooze: Boolean = this.dueOn != null && this.kind != null
    val canReschedule: Boolean = this.customId != null

    private val _effects = Channel<ReminderActionsEffect>(Channel.BUFFERED)
    val effects: Flow<ReminderActionsEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ReminderActionsEvent) {
        when (event) {
            ReminderActionsEvent.SnoozeTapped -> snooze()
            ReminderActionsEvent.TurnOffTapped -> turnOff()
            ReminderActionsEvent.RescheduleTapped -> reschedule()
        }
    }

    private fun snooze() {
        val kind = kind
        val dueOn = dueOn
        val carId = activeCar.activeCarId.value
        // A custom dismissal must name its reminder; a key that cannot is not actionable.
        if (kind == null || dueOn == null || carId == null || (kind == ReminderKind.CUSTOM && customId == null)) {
            close()
            return
        }
        viewModelScope.launch(telemetry.op(OP_SNOOZE)) {
            telemetry.dismiss(kind) {
                dismissReminder(
                    carId = carId,
                    dismissal = ReminderDismissal(
                        kind = kind,
                        dueOn = dueOn,
                        customId = if (kind == ReminderKind.CUSTOM) customId else null,
                    ),
                )
            }
            close()
        }
    }

    private fun turnOff() {
        val kind = kind ?: return close()
        viewModelScope.launch(telemetry.op(OP_TURN_OFF)) {
            val customId = customId
            if (customId != null) {
                telemetry.turnOff(kind, custom = true) { setPaused(customId, paused = true) }
            } else {
                telemetry.turnOff(kind, custom = false) {
                    val current = observeSettings().first()
                    val flipped = current.withTopicOff(kind)
                    // A kind with no matching topic (nothing to flip) succeeds as a no-op.
                    if (flipped == current) current.right() else updateSettings(flipped)
                }
            }
            close()
        }
    }

    private fun reschedule() {
        val customId = customId ?: return close()
        _effects.trySend(ReminderActionsEffect.OpenEdit(customId.value))
    }

    private fun close() {
        _effects.trySend(ReminderActionsEffect.Close)
    }

    private companion object {
        const val OP_SNOOZE = "snooze"
        const val OP_TURN_OFF = "turnOff"
    }
}

/** The topic that gates this kind, flipped off. Unchanged when no topic matches. */
private fun NotificationPreferences.withTopicOff(kind: ReminderKind): NotificationPreferences =
    when (kind) {
        ReminderKind.INSURANCE_EXPIRY,
        ReminderKind.PUC_EXPIRY,
        ReminderKind.LICENCE_EXPIRY,
        -> copy(documentExpiry = false)

        ReminderKind.SERVICE_DUE_KM,
        ReminderKind.SERVICE_DUE_TIME,
        -> copy(serviceDue = false)

        ReminderKind.HEALTH_DROP -> copy(healthScoreDrops = false)
        ReminderKind.INACTIVITY, ReminderKind.CUSTOM -> this
    }
