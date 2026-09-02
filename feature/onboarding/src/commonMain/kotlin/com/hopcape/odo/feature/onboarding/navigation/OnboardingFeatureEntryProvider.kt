package com.hopcape.odo.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoEffect
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoScreen
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoViewModel
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeEffect
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeScreen
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding's contribution to the navigation graph: registers the first-run pitch —
 * the [OdoDestination.Welcome] page and its video variant. Car setup moved to
 * `:feature:questionnaire` and registers [OdoDestination.Onboarding] itself. Collected by the `:app`
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
    }
}

/**
 * The Welcome route. Continue goes straight into car setup — no sign-in first. Odo is
 * offline-first, so first run has to reach a working car without an account; auth is
 * offered *after* setup, and only if there's no session (see `OnboardingEffect.Finish`).
 *
 * Sign in is the other door, for an owner who has done this before. Signing out or
 * reinstalling clears the local rows, so they arrive at an empty app with a full server.
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
            // Home, not car setup: sync restores the returning owner's car, so setup would
            // only make a second one. `popUpTo = Welcome` clears the pitch behind it, so
            // back from Home leaves the app rather than returning to first run.
            WelcomeEffect.OpenSignIn -> navigationManager.navigateTo(
                OdoDestination.Auth.Phone(next = OdoDestination.Home),
                popUpTo = OdoDestination.Welcome,
            )
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
