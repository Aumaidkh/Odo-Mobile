package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Moves between the pages of a flow that lives inside one screen.
 *
 * A permission flow is several pages deep but only one navigation destination, so nothing was
 * animating them: the frame simply became a different frame between one composition and the
 * next. That reads as a glitch rather than a step, and on a flow asking for something sensitive
 * it costs more than polish — an owner who cannot tell they moved forward cannot tell how far
 * through they are.
 *
 * The motion is the one `OdoNavHost` uses between destinations, at
 * [com.hopcape.odo.core.designsystem.theme.OdoMotion.flowMillis]: the incoming page eases in
 * from the leading edge while the outgoing one drifts the other way and fades. Stepping through
 * a flow and stepping between screens are the same gesture to the owner, so they are the same
 * motion here.
 *
 * The slide is deliberately shorter than the navigation host's. Between destinations almost
 * everything changes; between two pages of one flow the top bar and the buttons are often in the
 * same place with the same shape, and travelling them a long way draws the eye to the parts that
 * did not change.
 *
 * ```
 * OdoStepTransition(target = state.page, position = state.page.ordinal) { page ->
 *     when (page) { … }
 * }
 * ```
 *
 * @param target what to render. Everything [content] draws must come from this value rather than
 *   from the surrounding state: during the transition the outgoing page is still composed, and a
 *   page reading live state would redraw itself as the page it is being replaced by on its way
 *   out — a step counter jumping to the next number while sliding away.
 * @param position where [target] sits in the flow, counting from its start. Direction is worked
 *   out by comparing it with the page being left, so a step back animates as a step back without
 *   the caller tracking which way it went.
 */
@Composable
fun <T> OdoStepTransition(
    target: T,
    position: Int,
    modifier: Modifier = Modifier,
    label: String = "odoStep",
    content: @Composable (T) -> Unit,
) {
    // Read outside the spec: transitionSpec is not a composable, and the theme's motion tokens
    // are. The same reason OnboardingFlow reads them into a local first.
    val motion = OdoTheme.motion
    AnimatedContent(
        targetState = target to position,
        modifier = modifier,
        transitionSpec = {
            // Equal positions mean the page changed without moving through the flow — treated as
            // forward, which is the less surprising of the two.
            val forward = targetState.second >= initialState.second
            val fade = tween<Float>(motion.flowMillis, easing = motion.easeFlow)
            val slide = tween<IntOffset>(motion.flowMillis, easing = motion.easeFlow)
            val enter = slideInHorizontally(slide) { width ->
                if (forward) width / ENTER_FRACTION else -width / ENTER_FRACTION
            } + fadeIn(fade)
            val exit = slideOutHorizontally(slide) { width ->
                if (forward) -width / EXIT_FRACTION else width / EXIT_FRACTION
            } + fadeOut(fade)
            // Unclipped, and the container does not animate its size: these are full screens of
            // differing height, and letting the frame resize between them walks the bottom
            // buttons up and down in the middle of the slide.
            enter togetherWith exit using SizeTransform(clip = false) { _, _ -> snap() }
        },
        label = label,
    ) { (value, _) -> content(value) }
}

/** How far in the arriving page starts, as a fraction of the width. */
private const val ENTER_FRACTION = 8

/** How far the leaving page drifts — less than it arrives, so the two do not race. */
private const val EXIT_FRACTION = 20
