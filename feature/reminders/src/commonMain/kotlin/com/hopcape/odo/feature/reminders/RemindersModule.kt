package com.hopcape.odo.feature.reminders

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.reminders.domain.notification.NoOpReminderNotificationScheduler
import com.hopcape.odo.feature.reminders.domain.notification.ReminderNotificationScheduler
import com.hopcape.odo.feature.reminders.domain.usecase.CreateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.DeleteCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.DismissReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveRemindersUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.SetReminderPausedUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.navigation.RemindersFeatureEntryProvider
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

    single<ReminderNotificationScheduler> { NoOpReminderNotificationScheduler }

    factory {
        ObserveRemindersUseCase(
            documents = get(),
            serviceLogs = get(),
            reminders = get(),
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
}
