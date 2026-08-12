package com.hopcape.odo.feature.reminders.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.reminders.presentation.ReminderActionsSheetContent
import com.hopcape.odo.feature.reminders.presentation.ReminderIcon
import com.hopcape.odo.feature.reminders.presentation.RemindersEffect
import com.hopcape.odo.feature.reminders.presentation.RemindersEvent
import com.hopcape.odo.feature.reminders.presentation.RemindersScreen
import com.hopcape.odo.feature.reminders.presentation.RemindersViewModel
import com.hopcape.odo.feature.reminders.presentation.actions.ReminderActionsArgs
import com.hopcape.odo.feature.reminders.presentation.actions.ReminderActionsEffect
import com.hopcape.odo.feature.reminders.presentation.actions.ReminderActionsEvent
import com.hopcape.odo.feature.reminders.presentation.actions.ReminderActionsViewModel
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderArgs
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderEffect
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderEvent
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderScreen
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderViewModel
import com.hopcape.odo.feature.reminders.presentation.settings.ReminderSettingsEffect
import com.hopcape.odo.feature.reminders.presentation.settings.ReminderSettingsEvent
import com.hopcape.odo.feature.reminders.presentation.settings.ReminderSettingsScreen
import com.hopcape.odo.feature.reminders.presentation.settings.ReminderSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Reminders' contribution to the navigation graph: the [OdoDestination.Reminders] group.
 * Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 */
internal class RemindersFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Reminders.List> { RemindersRoute(navigationManager) }
        entry<OdoDestination.Reminders.Settings> { ReminderSettingsRoute(navigationManager) }
        entry<OdoDestination.Reminders.New> { key -> NewReminderRoute(navigationManager, key) }
        entry<OdoDestination.Reminders.Actions>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) { key ->
            ReminderActionsRoute(key, navigationManager)
        }
    }
}

/** The reminders home route host. */
@Composable
internal fun RemindersRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<RemindersViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is RemindersEffect.OpenActions -> navigationManager.navigateTo(
                OdoDestination.Reminders.Actions(
                    kind = effect.kind,
                    dueOn = effect.dueOn,
                    customId = effect.customId,
                    title = effect.title,
                    due = effect.due,
                    icon = effect.icon,
                ),
            )

            RemindersEffect.OpenSettings -> navigationManager.navigateTo(OdoDestination.Reminders.Settings)
            RemindersEffect.OpenNew -> navigationManager.navigateTo(OdoDestination.Reminders.New())
        }
    }

    RemindersScreen(
        state = state,
        onManage = { viewModel.onEvent(RemindersEvent.ManageTapped) },
        onOpenActions = { row, title, due -> viewModel.onEvent(RemindersEvent.ReminderTapped(row, title, due)) },
        onRemindMe = { preset, name -> viewModel.onEvent(RemindersEvent.SuggestionTapped(preset, name)) },
        onAdd = { viewModel.onEvent(RemindersEvent.AddTapped) },
    )
}

/**
 * The reminder-actions route host — the this-week actions sheet. The key's primitives
 * carry both the identity the ViewModel acts on and the display echo the sheet renders.
 */
@Composable
internal fun ReminderActionsRoute(
    key: OdoDestination.Reminders.Actions,
    navigationManager: NavigationManager,
) {
    val viewModel = koinViewModel<ReminderActionsViewModel> {
        parametersOf(ReminderActionsArgs(kind = key.kind, dueOn = key.dueOn, customId = key.customId))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ReminderActionsEffect.Close -> navigationManager.back()

            is ReminderActionsEffect.OpenEdit -> {
                // The sheet closes and the edit form opens over the list.
                navigationManager.back()
                navigationManager.navigateTo(OdoDestination.Reminders.New(reminderId = effect.reminderId))
            }
        }
    }

    ReminderActionsSheetContent(
        icon = runCatching { ReminderIcon.valueOf(key.icon) }.getOrDefault(ReminderIcon.OIL),
        title = key.title,
        due = key.due,
        showReschedule = viewModel.canReschedule,
        showSnooze = viewModel.canSnooze,
        onReschedule = { viewModel.onEvent(ReminderActionsEvent.RescheduleTapped) },
        onSnooze = { viewModel.onEvent(ReminderActionsEvent.SnoozeTapped) },
        onTurnOff = { viewModel.onEvent(ReminderActionsEvent.TurnOffTapped) },
    )
}

/** The new/edit-reminder route host. */
@Composable
internal fun NewReminderRoute(
    navigationManager: NavigationManager,
    key: OdoDestination.Reminders.New,
) {
    val viewModel = koinViewModel<NewReminderViewModel> {
        parametersOf(NewReminderArgs(reminderId = key.reminderId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            NewReminderEffect.OpenSettings -> navigationManager.navigateTo(OdoDestination.Reminders.Settings)
            NewReminderEffect.Close -> navigationManager.back()
        }
    }

    NewReminderScreen(
        state = state,
        onPresetSelect = { preset, defaultName -> viewModel.onEvent(NewReminderEvent.PresetSelected(preset, defaultName)) },
        onCustomSave = { viewModel.onEvent(NewReminderEvent.CustomLabelSaved(it)) },
        onNameChange = { viewModel.onEvent(NewReminderEvent.NameChanged(it)) },
        onRepeatChange = { viewModel.onEvent(NewReminderEvent.RepeatChanged(it)) },
        onStartChange = { viewModel.onEvent(NewReminderEvent.StartChanged(it)) },
        onTimeChange = { hour, minute -> viewModel.onEvent(NewReminderEvent.TimeChanged(hour, minute)) },
        onChangeChannels = { viewModel.onEvent(NewReminderEvent.ChangeChannelsTapped) },
        onSave = { viewModel.onEvent(NewReminderEvent.SaveTapped) },
        onClose = { viewModel.onEvent(NewReminderEvent.CloseTapped) },
    )
}

/** The reminder-settings route host. */
@Composable
internal fun ReminderSettingsRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<ReminderSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ReminderSettingsEffect.NavigateBack -> navigationManager.back()
        }
    }

    ReminderSettingsScreen(
        state = state,
        onToggle = { viewModel.onEvent(ReminderSettingsEvent.Toggled(it)) },
        onBack = { viewModel.onEvent(ReminderSettingsEvent.BackTapped) },
    )
}
