package com.hopcape.odo.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.finishFlow
import com.hopcape.odo.core.navigation.isFirstRunStep
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.onboarding.presentation.OnboardingEffect
import com.hopcape.odo.feature.onboarding.presentation.OnboardingFlow
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoEffect
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoScreen
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoViewModel
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeEffect
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeScreen
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding's contribution to the navigation graph: registers the whole first-run
 * flow — the [OdoDestination.Welcome] pitch and [OdoDestination.Onboarding] car setup.
 * Both belong to this one feature, so they're registered by this one provider (a
 * `registerEntries` block can contribute any number of entries). Collected by the `:app`
 * host (`getAll<FeatureEntryProvider>()`), so no other module references onboarding.
 *
 * The routes below are **only** a state-and-effects bridge: they render a ViewModel's state,
 * forward its events, and translate its effects into navigation commands. Every decision
 * about *what* should happen is made in presentation, which is why the `when` blocks here
 * contain no logic beyond building the key.
 */
internal class OnboardingFeatureEntryProvider(
    private val navigationManager: NavigationManager,
    private val legalLinks: LegalLinks,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Welcome> { WelcomeRoute(navigationManager, legalLinks) }
        entry<OdoDestination.WelcomeVideo> { WelcomeVideoRoute(navigationManager) }
        entry<OdoDestination.Onboarding> { OnboardingRoute(navigationManager) }
    }
}

/**
 * The Welcome route. Continue goes straight into car setup — no sign-in first. Odo is
 * offline-first, so first run has to reach a working car without an account; auth is
 * offered *after* setup, and only if there's no session (see [OnboardingEffect.Finish]).
 *
 * The two legal taps open the hosted pages in the platform browser, the same hand-off the
 * privacy screen in `:feature:support` makes. They leave the app on purpose: an in-app
 * browser would hide the address of a document whose whole point is being verifiable.
 */
@Composable
internal fun WelcomeRoute(navigationManager: NavigationManager, legalLinks: LegalLinks) {
    val viewModel = koinViewModel<WelcomeViewModel>()
    val uriHandler = LocalUriHandler.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WelcomeEffect.OpenCarSetup -> navigationManager.navigateTo(OdoDestination.Onboarding)
            // A build with no backend configured gets blank URLs, and the tap then does
            // nothing. The sentence around the links is a legal statement, so unlike the
            // support screen's rows it has to render either way — there is nothing to hide.
            WelcomeEffect.OpenTerms -> legalLinks.termsOfUse.openIfSet(uriHandler)
            WelcomeEffect.OpenPrivacy -> legalLinks.privacyPolicy.openIfSet(uriHandler)
        }
    }
    WelcomeScreen(onEvent = viewModel::onEvent)
}

/** Opens this URL, or does nothing when the build has no legal pages configured. */
private fun String.openIfSet(uriHandler: UriHandler) {
    if (isNotBlank()) uriHandler.openUri(this)
}

/**
 * The setup route — steps 2 to 4 behind one destination, because they are one form: back
 * moves between steps instead of popping screens, and the header's progress stays
 * continuous across them.
 *
 * Every way out of the last step is a finish, including its camera button. The flow does
 * not make a round trip to the scanner and come back, so the entry is popped once and the
 * first run is over.
 */
@Composable
internal fun OnboardingRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.NavigateBack -> navigationManager.back()

            // TODO(ui): show this in a snackbar. Every step screen scaffolds itself with its
            //  own OdoScreen, so the host state has to be threaded through OnboardingFlow
            //  before there is anywhere to post it. Until then a failed write is visible
            //  only as Continue not advancing — which is honest, but not an explanation.
            is OnboardingEffect.SaveFailed -> Unit

            is OnboardingEffect.Finish -> {
                val destination = effect.start.toOdoDestination()
                // The intro and the setup steps leave the back stack — first run doesn't
                // repeat. finishFlow rather than popUpTo(Welcome), because the flow's root
                // is whichever intro the remote flag chose, and popping up to the wrong
                // one silently left the whole first run under the landing screen (#352).
                if (effect.openScanner) {
                    // The start surface is seeded *under* the scanner rather than replaced by
                    // it. Leaving the scan errand pops its own steps and lands on whatever is
                    // below them, so with the scanner alone on the stack there would be
                    // nothing to land on and the owner would be stuck on the viewfinder.
                    navigationManager.finishFlow(destination, ::isFirstRunStep)
                    val scanner = OdoDestination.BillScanner.Capture()
                    // Sign-in still comes first; auth carries the scanner as its `next`, so
                    // both verifying and skipping arrive at the same viewfinder.
                    navigationManager.navigateTo(
                        if (effect.signInFirst) OdoDestination.Auth.Phone(scanner) else scanner,
                    )
                } else {
                    val next =
                        if (effect.signInFirst) OdoDestination.Auth.Phone(destination) else destination
                    navigationManager.finishFlow(next, ::isFirstRunStep)
                }
            }
        }
    }

    OnboardingFlow(state = state, onEvent = viewModel::onEvent)
}

/**
 * The video intro, shown instead of [WelcomeRoute] when `onboarding_video_enabled` is on.
 *
 * Both finishing and skipping land in the same place the welcome page leads: skipping the
 * intro is not skipping onboarding, and there is no version of first run that does not set
 * up a car.
 */
@Composable
internal fun WelcomeVideoRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<WelcomeVideoViewModel>()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WelcomeVideoEffect.OpenCarSetup -> navigationManager.navigateTo(OdoDestination.Onboarding)
        }
    }
    WelcomeVideoScreen(pages = viewModel.pages, onEvent = viewModel::onEvent)
}
