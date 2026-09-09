package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

internal typealias AppStatusTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** What the gate puts on screen. Mirrors `shared`'s `strings.xml` (`as_*`). */
internal object AppStatusCopy {
    const val MAINTENANCE_TITLE = "Under maintenance"
    const val MAINTENANCE_BODY = "We're making things better. Please check back shortly."
    const val UPDATE_TITLE = "Update required"
    const val UPDATE_NOW = "Update now"
    const val RETRY = "Try again"
}

/**
 * One verdict for the whole process, whose value the tests move.
 *
 * Bound once and mutated afterwards, for the reason [installCatalog]'s switchable catalog
 * exists: `App()` resolves [AppStatusProvider] when it first composes, so rebinding it in Koin
 * later changes what a future composition would get and nothing about the live one.
 */
private object SwitchableAppStatus : AppStatusProvider {
    val state = MutableStateFlow<AppAvailability>(AppAvailability.Allowed)
    override val availability: StateFlow<AppAvailability> = state.asStateFlow()

    /** The real provider refetches; here the test is the only thing that moves the verdict. */
    override suspend fun refresh() = Unit
}

private var appStatusBound = false

/** Put the app on [availability], binding the switchable provider the first time round. */
internal fun installAppStatus(availability: AppAvailability) {
    SwitchableAppStatus.state.value = availability
    if (appStatusBound) return
    GlobalContext.get().loadModules(
        listOf(module { single<AppStatusProvider> { SwitchableAppStatus } }),
        allowOverride = true,
    )
    appStatusBound = true
}

/** A full-block maintenance window. [message] null falls back to the built-in copy. */
internal fun installMaintenanceBlock(message: String? = null) =
    installAppStatus(AppAvailability.Blocked.Maintenance(message))

/** The build is below `min_supported_version_code`. */
internal fun installUpdateRequired() = installAppStatus(AppAvailability.Blocked.UpdateRequired)

/** Restore the shipped state, so a later test in this process is not still blocked. */
internal fun installAppAllowed() = installAppStatus(AppAvailability.Allowed)
