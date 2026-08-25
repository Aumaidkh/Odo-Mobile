package com.hopcape.odo.core.platform.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * What a player can be doing, as far as a screen needs to care.
 *
 * [Failed] is not an error to show as an error. A clip that will not load is a decoration
 * that did not arrive: the screen it sits on still has copy, a title and a working Continue,
 * and losing the video must never cost the owner the step. See [OdoVideoPlayer].
 */
enum class OdoVideoStatus { Loading, Playing, Failed }

/**
 * How a clip is fitted to the space it is given.
 *
 * [Fill] crops whatever does not fit, so the video reaches every edge; [Fit] keeps the whole
 * frame and letterboxes the rest. Fill is the default because the usual caller is a clip
 * used as a surface, where bars down the sides read as a mistake rather than as respect for
 * the aspect ratio.
 */
enum class OdoVideoFit { Fill, Fit }

/**
 * Held by the caller so the screen can react to a clip that never arrives — the whole
 * reason this is a state holder and not a fire-and-forget composable.
 */
class OdoVideoState internal constructor(internal val status: MutableState<OdoVideoStatus>) {
    val value: OdoVideoStatus get() = status.value
    val hasFailed: Boolean get() = status.value == OdoVideoStatus.Failed
}

@Composable
fun rememberOdoVideoState(): OdoVideoState =
    remember { OdoVideoState(mutableStateOf(OdoVideoStatus.Loading)) }

/**
 * Plays [url], looping and muted, with no transport controls.
 *
 * Muted and controlless on purpose: this exists to show a feature working behind onboarding
 * copy, not to be watched. Sound on a first launch is startling, and a scrub bar invites an
 * interaction that leads nowhere.
 *
 * **Streamed, not bundled.** The clips live behind remote URLs so they can be replaced
 * without a release. That is a deliberate trade — it means the first launch of a device with
 * no network gets no video — so every caller must stay usable when [state] reports
 * [OdoVideoStatus.Failed], and a blank [url] must be treated the same way rather than
 * handed to a player.
 */
@Composable
expect fun OdoVideoPlayer(
    url: String,
    state: OdoVideoState,
    modifier: Modifier = Modifier,
    fit: OdoVideoFit = OdoVideoFit.Fill,
)
