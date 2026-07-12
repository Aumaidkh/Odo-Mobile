package com.hopcape.odo.feature.reminders.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.reminders.presentation.RemindersScreen
import com.hopcape.odo.feature.reminders.presentation.sampleRemindersAttention
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderScreen
import com.hopcape.odo.feature.reminders.presentation.create.sampleNewReminder
import com.hopcape.odo.feature.reminders.presentation.settings.ReminderSettingsScreen
import com.hopcape.odo.feature.reminders.presentation.settings.sampleReminderSettings
import kotlin.time.Clock

/**
 * Reminders' contribution to the navigation graph: the [OdoDestination.Reminders] group —
 * the [OdoDestination.Reminders.List] home and its [OdoDestination.Reminders.Settings].
 * Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 */
internal class RemindersFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Reminders.List> { RemindersRoute(navigationManager) }
        entry<OdoDestination.Reminders.Settings> { ReminderSettingsRoute(navigationManager) }
        entry<OdoDestination.Reminders.New> { NewReminderRoute(navigationManager) }
    }
}

/**
 * The reminders route host — renders sample reminders until the reminder engine (renewal
 * triggers + schedules) lands. "Manage" opens the settings; open / remind-me / add are M2 stubs.
 */
@Composable
internal fun RemindersRoute(navigationManager: NavigationManager) {
    RemindersScreen(
        state = sampleRemindersAttention(),
        onManage = { navigationManager.navigateTo(OdoDestination.Reminders.Settings) },
        onRemindMe = { /* TODO(M2): opt into a suggested reminder. */ },
        onAdd = { navigationManager.navigateTo(OdoDestination.Reminders.New) },
    )
}

/**
 * The new-reminder route host — holds the create-form state locally until the
 * reminder-creation use case (persisting a schedule) lands. "Save" and the close
 * button both pop back for now; "Change" jumps to the notification settings.
 */
@Composable
internal fun NewReminderRoute(navigationManager: NavigationManager) {
    // Seed the start date to today (UTC midnight, matching the date picker's millis).
    val todayMillis = (Clock.System.now().toEpochMilliseconds() / 86_400_000L) * 86_400_000L
    var state by remember { mutableStateOf(sampleNewReminder(startMillis = todayMillis)) }
    NewReminderScreen(
        state = state,
        onPresetSelect = { preset, defaultName -> state = state.selectPreset(preset, defaultName) },
        onCustomSave = { state = state.withCustomLabel(it) },
        onNameChange = { state = state.copy(name = it) },
        onRepeatChange = { state = state.copy(repeat = it) },
        onStartChange = { state = state.copy(startMillis = it) },
        onTimeChange = { hour, minute -> state = state.copy(hour = hour, minute = minute) },
        onChangeChannels = { navigationManager.navigateTo(OdoDestination.Reminders.Settings) },
        onSave = { navigationManager.back() },
        onClose = { navigationManager.back() },
    )
}

/**
 * The reminder-settings route host — holds the toggle + lead-time state locally until the
 * settings ViewModel (persisting to the reminder engine) lands.
 */
@Composable
internal fun ReminderSettingsRoute(navigationManager: NavigationManager) {
    var state by remember { mutableStateOf(sampleReminderSettings()) }
    ReminderSettingsScreen(
        state = state,
        onToggle = { state = state.toggled(it) },
        onRemindBeforeChange = { state = state.copy(remindBefore = it) },
        onBack = { navigationManager.back() },
    )
}
