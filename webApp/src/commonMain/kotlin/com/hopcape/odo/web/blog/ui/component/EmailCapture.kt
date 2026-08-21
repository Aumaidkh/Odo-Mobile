package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.presentation.state.FormField
import com.hopcape.odo.web.blog.presentation.state.Submission
import com.hopcape.odo.web.blog.presentation.state.resolve
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_email_placeholder
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The one email form on the public side, used twice: subscribe on a thin
 * category, and request a topic on an empty search.
 *
 * Written once because the two are the same interaction — an address, a button,
 * and a line of copy that replaces both once it has been accepted. The design
 * draws them identically, and two copies would not stay identical.
 *
 * On success the form is gone rather than disabled. A cleared box next to a
 * "thanks" invites a second submission that does nothing.
 */
@Composable
fun EmailCapture(
    heading: String,
    dek: String,
    action: String,
    doneMessage: String,
    email: FormField<String>,
    submission: Submission,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(heading, color = colors.text, style = MaterialTheme.typography.headlineSmall)
        Text(dek, color = colors.dim, style = MaterialTheme.typography.bodyMedium)

        when (submission) {
            Submission.Done -> Text(
                text = doneMessage,
                color = colors.success,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.padding(top = 6.dp).widthIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BlogTextField(
                        value = email.value,
                        onValueChange = onEmailChange,
                        placeholder = stringResource(Res.string.bl_email_placeholder),
                        onSubmit = onSubmit,
                        modifier = Modifier.weight(1f),
                    )
                    if (!compact) {
                        PillButton(action, onSubmit, enabled = submission != Submission.Sending)
                    }
                }
                if (compact) {
                    PillButton(action, onSubmit, enabled = submission != Submission.Sending)
                }
                // The field's own complaint first — it is about what was typed.
                // A submission failure is about the request, and only one of the
                // two can be true at a time.
                email.error?.let {
                    Text(it.resolve(), color = colors.danger, style = MaterialTheme.typography.bodyMedium)
                }
                (submission as? Submission.Failed)?.let {
                    Text(it.message.resolve(), color = colors.danger, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
