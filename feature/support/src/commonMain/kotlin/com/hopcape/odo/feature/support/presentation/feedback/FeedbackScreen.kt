package com.hopcape.odo.feature.support.presentation.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCheckboxRow
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_fb_attach_diagnostics
import com.hopcape.odo.feature.support.resources.sp_fb_attach_diagnostics_sub
import com.hopcape.odo.feature.support.resources.sp_fb_attach_note
import com.hopcape.odo.feature.support.resources.sp_fb_hint
import com.hopcape.odo.feature.support.resources.sp_fb_send
import org.jetbrains.compose.resources.stringResource

/**
 * The form behind "Report a problem", "Suggest an idea" and "Flag wrong price data".
 *
 * One screen for all three: they differ only by [title] and [intro], and by the subject the
 * caller puts on the mail. Three near-identical screens would drift apart the first time one
 * of them was adjusted.
 *
 * It owns nothing but the text being typed. There is no draft to persist and nothing to
 * save — the message lives here until it is handed to the mail app, and after that the mail
 * app owns it. That is also why there is no view model: no domain call, no result to
 * collect, and the mail hand-off is a composable seam a view model could not hold.
 *
 * The box opens already filled in, with [template]'s headings. An empty box and a blinking
 * cursor is the point most people give up at — they know what went wrong but not what we
 * need to hear, so they write one line or close the screen. Headings turn writing a report
 * into filling in blanks, and anything left blank still sends.
 *
 * [showAttachDiagnostics] is on for the problem report only. An idea or a wrong benchmark has
 * no logs worth reading, and offering to send some there would be asking for data nobody
 * would open. It defaults to ticked because a problem report without logs is the one support
 * has to answer with "can you send us diagnostics", which costs the owner another round trip.
 *
 * @param template the headings the message starts as.
 * @param onSend called with the typed message, and whether logs should go with it, once
 *   something has been added to [template].
 */
@Composable
internal fun FeedbackScreen(
    title: String,
    intro: String,
    template: String,
    onBack: () -> Unit,
    onSend: (message: String, attachDiagnostics: Boolean) -> Unit,
    showAttachDiagnostics: Boolean = false,
) {
    // Survives rotation. Losing a paragraph of a bug report to a screen turn is the sort of
    // thing that stops somebody reporting the bug at all.
    var message by rememberSaveable { mutableStateOf(template) }
    // Survives rotation for the same reason the message does: an unticked box that quietly
    // re-ticks itself is consent nobody gave.
    var attachDiagnostics by rememberSaveable { mutableStateOf(true) }
    // Untouched headings are not a report. Comparing against the template rather than
    // checking for blankness is what stops the prefill making Send look ready to press
    // before anything has been written.
    val canSend = message.isNotBlank() && message.trim() != template.trim()

    OdoScreen(title = title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoText(intro, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)

            OdoInputField(
                value = message,
                onValueChange = { message = it },
                placeholder = stringResource(Res.string.sp_fb_hint),
                singleLine = false,
                // Tall enough to look like somewhere to write a paragraph. A single-line
                // box invites a single line, and one line is rarely enough to act on.
                modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_FIELD_MIN_HEIGHT),
            )

            if (showAttachDiagnostics) {
                OdoCard {
                    OdoCheckboxRow(
                        label = stringResource(Res.string.sp_fb_attach_diagnostics),
                        checked = attachDiagnostics,
                        onCheckedChange = { attachDiagnostics = it },
                    )
                    // What "diagnostics" actually means, next to the box rather than in a
                    // policy: somebody ticking it has no other way to know whether their
                    // bills and papers are part of it.
                    OdoText(
                        stringResource(Res.string.sp_fb_attach_diagnostics_sub),
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textMuted,
                    )
                }
            }

            OdoText(
                stringResource(Res.string.sp_fb_attach_note),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
            )

            OdoButton(
                text = stringResource(Res.string.sp_fb_send),
                onClick = { onSend(message.trim(), showAttachDiagnostics && attachDiagnostics) },
                // Disabled rather than validated on tap: an empty report helps nobody, and
                // an error message telling somebody to type something they can see they have
                // not typed is noise.
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val MESSAGE_FIELD_MIN_HEIGHT = 160.dp
