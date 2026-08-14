package com.hopcape.odo.feature.reminders

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.reminders.domain.usecase.CreateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.DeleteCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.DismissReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCurrentOdometerUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveRemindersUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.SetReminderPausedUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.navigation.RemindersFeatureEntryProvider
import com.hopcape.odo.feature.reminders.presentation.RemindersTelemetry
import com.hopcape.odo.feature.reminders.presentation.RemindersViewModel
import com.hopcape.odo.feature.reminders.presentation.actions.ReminderActionsArgs
import com.hopcape.odo.feature.reminders.presentation.actions.ReminderActionsViewModel
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderArgs
import com.hopcape.odo.feature.reminders.presentation.create.NewReminderViewModel
import com.hopcape.odo.feature.reminders.presentation.settings.ReminderSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the reminders feature. `NavigationManager` comes from
 * `coreNavigationModule`; `DocumentRepository`, `ServiceLogRepository`,
 * `ReminderRepository` and `AppSettingsRepository` from `coreDataModule`; `IdGenerator`
 * + `Clock` from `coreCommonModule`. The `:app` host registers them all.
 *
 * The notification scheduler is the no-op until the M4 engine lands — swapping in the
 * real one is one line here.
 */
val remindersModule = module {
    single {
        RemindersFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class


    factory {
        ObserveRemindersUseCase(
            documents = get(),
            serviceLogs = get(),
            reminders = get(),
            currentOdometer = get(),
            clock = get(),
        )
    }
    factory {
        CreateCustomReminderUseCase(
            reminders = get(),
            scheduler = get(),
            idGenerator = get(),
            clock = get(),
        )
    }
    factory { UpdateCustomReminderUseCase(reminders = get(), scheduler = get(), clock = get()) }
    factory { SetReminderPausedUseCase(reminders = get(), scheduler = get()) }
    factory { DeleteCustomReminderUseCase(reminders = get(), scheduler = get()) }
    factory { DismissReminderUseCase(reminders = get()) }
    factory { ObserveReminderSettingsUseCase(appSettings = get()) }
    factory { UpdateReminderSettingsUseCase(appSettings = get()) }
    factory { ObserveCurrentOdometerUseCase(currentOdometer = get()) }
    factory { ObserveCustomReminderUseCase(reminders = get()) }

    // A `factory`, not a `single`: one instance covers one visit to reminders, and every
    // screen of that visit shares one flow id.
    factory { RemindersTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel {
        RemindersViewModel(
            activeCar = get(),
            observeReminders = get(),
            observeOdometer = get(),
            createReminder = get(),
            owners = get(),
            telemetry = get(),
            clock = get(),
        )
    }
    viewModel { params ->
        ReminderActionsViewModel(
            args = params.get<ReminderActionsArgs>(),
            activeCar = get(),
            dismissReminder = get(),
            setPaused = get(),
            observeSettings = get(),
            updateSettings = get(),
            telemetry = get(),
        )
    }
    viewModel { params ->
        NewReminderViewModel(
            args = params.get<NewReminderArgs>(),
            activeCar = get(),
            owners = get(),
            observeOdometer = get(),
            observeReminder = get(),
            createReminder = get(),
            updateReminder = get(),
            telemetry = get(),
            clock = get(),
        )
    }
    viewModel {
        ReminderSettingsViewModel(
            observeSettings = get(),
            updateSettings = get(),
            telemetry = get(),
        )
    }
}
