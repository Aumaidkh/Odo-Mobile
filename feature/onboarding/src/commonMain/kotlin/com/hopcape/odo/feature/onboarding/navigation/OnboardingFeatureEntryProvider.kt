package com.hopcape.odo.feature.onboarding.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.onboarding.presentation.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.OnboardingUiState
import com.hopcape.odo.feature.onboarding.presentation.PlateLookup
import com.hopcape.odo.feature.onboarding.presentation.PlateLookupError
import com.hopcape.odo.feature.onboarding.presentation.car.CarDetailsStepScreen
import com.hopcape.odo.feature.onboarding.presentation.car.CarStepScreen
import com.hopcape.odo.feature.onboarding.presentation.profile.ProfileStepScreen
import com.hopcape.odo.feature.onboarding.presentation.sampleMakes
import com.hopcape.odo.feature.onboarding.presentation.sampleModels
import com.hopcape.odo.feature.onboarding.presentation.samplePopularMakes
import com.hopcape.odo.feature.onboarding.presentation.scan.FirstScanScreen
import com.hopcape.odo.feature.onboarding.presentation.toDomain
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeScreen
import kotlinx.coroutines.delay

/**
 * Onboarding's contribution to the navigation graph: registers the whole first-run
 * flow — the [OdoDestination.Welcome] pitch and [OdoDestination.Onboarding] car setup.
 * Both belong to this one feature, so they're registered by this one provider (a
 * `registerEntries` block can contribute any number of entries). Collected by the `:app`
 * host (`getAll<FeatureEntryProvider>()`), so no other module references onboarding.
 *
 * **No ViewModel yet, by design.** The redesigned screens are stateless and the routes
 * below hold their state in `remember`, the same shape the paywall route uses. When the
 * ViewModel lands it replaces those `remember` blocks and the stubs marked TODO — the
 * screens don't change.
 */
internal class OnboardingFeatureEntryProvider(
    private val navigationManager: NavigationManager,
    private val sessionStatus: SessionStatusProvider,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Welcome> { WelcomeRoute(navigationManager) }
        entry<OdoDestination.Onboarding> { OnboardingRoute(navigationManager, sessionStatus) }
    }
}

/**
 * The Welcome route. Continue goes straight into car setup — no sign-in first. Odo is
 * offline-first, so first run has to reach a working car without an account; auth is
 * offered *after* setup, and only if there's no session (see `finish` in [OnboardingRoute]).
 */
@Composable
internal fun WelcomeRoute(navigationManager: NavigationManager) {
    WelcomeScreen(
        onContinue = { navigationManager.navigateTo(OdoDestination.Onboarding) },
        onTerms = { /* TODO: open the Terms page. */ },
        onPrivacy = { /* TODO: open the Privacy Policy page. */ },
    )
}

/**
 * The setup route — steps 2 to 4 behind one destination, because they are one form: back
 * moves between steps instead of popping screens, and the header's progress stays
 * continuous across them.
 *
 * State lives here until a ViewModel owns it, and nothing is persisted — leaving and
 * returning starts over, which is fine while the car isn't actually saved yet.
 */
@Composable
internal fun OnboardingRoute(
    navigationManager: NavigationManager,
    sessionStatus: SessionStatusProvider,
) {
    var state by remember {
        mutableStateOf(
            // Placeholder reference data, standing in for the VehicleCatalog port.
            OnboardingUiState(
                makes = sampleMakes,
                popularMakes = samplePopularMakes,
                models = sampleModels,
            ),
        )
    }
    // Bumped by "Try again" so the effect below re-runs on an unchanged plate.
    var lookupAttempt by remember { mutableIntStateOf(0) }

    // The plate lookup: starts itself when the plate is complete, cancels itself when the
    // owner keeps typing (LaunchedEffect is keyed on the plate, so an in-flight lookup for
    // a stale plate can never land on screen).
    LaunchedEffect(state.car.registrationNumber, lookupAttempt) {
        if (!state.car.isPlateLookupReady) return@LaunchedEffect
        state = state.copy(car = state.car.copy(lookup = PlateLookup.Loading))
        delay(StubLookupDelayMillis)
        // TODO(rto): call the real lookup here (Edge Function → registry) and map its
        //  result to Found / Failed(NOT_FOUND | OFFLINE | SERVICE). Until that exists this
        //  reports SERVICE — truthful, since no lookup service is reachable — and never
        //  invents a car, because a wrong match would poison every price benchmark.
        state = state.copy(car = state.car.copy(lookup = PlateLookup.Failed(PlateLookupError.SERVICE)))
    }

    /**
     * Where the owner lands once setup is done — their goal picks the surface (PRD §5.1).
     *
     * This is also the one point where sign-in is offered: the car exists, so there is
     * finally something worth protecting, and the ask can be concrete ("back up *these*
     * records") instead of an account wall on a blank app. It is offered **only** when
     * there's no session, and auth carries [destination] so it can hand the owner onward
     * whether they verify or skip.
     */
    fun finish() {
        val destination = state.profile.goal
            ?.toDomain()
            ?.toStartDestination()
            ?.toOdoDestination()
            ?: OdoDestination.Home
        val next = if (sessionStatus.isSignedIn()) destination else OdoDestination.Auth.Phone(destination)
        // Welcome and the setup steps leave the back stack — first run doesn't repeat.
        navigationManager.navigateTo(next, popUpTo = OdoDestination.Welcome, inclusive = true)
    }

    fun advance() {
        val next = state.step.next
        if (next == null) finish() else state = state.copy(step = next)
    }

    fun goBack() {
        if (state.step == OnboardingStep.CAR && state.car.manualEntry) {
            // Manual entry is a mode of the car step, so back returns to the plate first.
            state = state.copy(car = state.car.copy(manualEntry = false))
            return
        }
        val previous = state.step.previous
        if (previous == null) navigationManager.back() else state = state.copy(step = previous)
    }

    // Read outside the spec: transitionSpec isn't composable, and the theme's motion
    // tokens are.
    val motion = OdoTheme.motion
    AnimatedContent(
        targetState = state.step to state.car.manualEntry,
        transitionSpec = {
            fadeIn(tween(motion.baseMillis, easing = motion.easeStandard)) togetherWith
                fadeOut(tween(motion.baseMillis / 2))
        },
        label = "onboardingStep",
    ) { (step, manualEntry) ->
        when (step) {
            OnboardingStep.CAR -> if (manualEntry) {
                CarDetailsStepScreen(
                    state = state,
                    onMakeChange = { make ->
                        // A new brand invalidates the model chosen under the old one.
                        state = state.copy(car = state.car.copy(make = make, model = null, variant = null))
                    },
                    onModelChange = { model ->
                        state = state.copy(car = state.car.copy(model = model.name, variant = model.variant))
                    },
                    onYearChange = { year -> state = state.copy(car = state.car.copy(year = year)) },
                    onFuelChange = { fuel -> state = state.copy(car = state.car.copy(fuelType = fuel)) },
                    onTryAutoFill = { state = state.copy(car = state.car.copy(manualEntry = false)) },
                    onBack = ::goBack,
                    onContinue = ::advance,
                )
            } else {
                CarStepScreen(
                    state = state,
                    onPlateChange = { plate ->
                        // Editing the plate drops whatever the last one resolved to; the
                        // effect above starts a fresh lookup once it's complete again.
                        state = state.copy(
                            car = state.car.copy(registrationNumber = plate, lookup = PlateLookup.Idle),
                        )
                    },
                    onOdometerChange = { km -> state = state.copy(car = state.car.copy(odometerKm = km)) },
                    onRetryLookup = { lookupAttempt++ },
                    onEnterManually = {
                        state = state.copy(
                            car = state.car.copy(manualEntry = true, lookup = PlateLookup.Idle),
                        )
                    },
                    onBack = ::goBack,
                    onContinue = ::advance,
                )
            }

            OnboardingStep.PROFILE -> ProfileStepScreen(
                state = state,
                onNameChange = { name -> state = state.copy(profile = state.profile.copy(name = name)) },
                onGoalChange = { goal -> state = state.copy(profile = state.profile.copy(goal = goal)) },
                onBack = ::goBack,
                onContinue = ::advance,
            )

            OnboardingStep.FIRST_SCAN -> FirstScanScreen(
                // TODO(billscanner): hand the saved car to BillScanner.Capture and come back.
                onScan = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture) },
                onSkip = ::finish,
            )
        }
    }
}

/**
 * How long the stubbed lookup "takes". Roughly a real round trip, so the loading state is
 * exercised honestly rather than flashing past.
 */
private const val StubLookupDelayMillis = 1_400L
