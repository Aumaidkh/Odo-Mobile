package com.hopcape.odo.web.blog.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.hopcape.odo.web.blog.presentation.admin.signin.SignInEvent
import com.hopcape.odo.web.blog.presentation.admin.signin.SignInUiState
import com.hopcape.odo.web.blog.presentation.state.resolve
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_admin_email
import com.hopcape.odo.web.blog.resources.bl_admin_forgot
import com.hopcape.odo.web.blog.resources.bl_admin_password
import com.hopcape.odo.web.blog.resources.bl_admin_sign_in
import com.hopcape.odo.web.blog.resources.bl_admin_sign_in_dek
import com.hopcape.odo.web.blog.resources.bl_admin_sign_in_heading
import com.hopcape.odo.web.blog.resources.bl_brand
import com.hopcape.odo.web.blog.ui.component.LabelledField
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The only page in the CMS that a signed-out person can see.
 *
 * A card in the middle of an empty page, with no navigation on it: there is
 * nowhere else to go from here, and offering links would be offering doors that
 * are locked.
 */
@Composable
fun SignInScreen(
    state: SignInUiState,
    onEvent: (SignInEvent) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(Res.string.bl_brand),
                color = colors.text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.34.em,
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(Res.string.bl_admin_sign_in_heading),
                    color = colors.text,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(Res.string.bl_admin_sign_in_dek),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LabelledField(
                label = stringResource(Res.string.bl_admin_email),
                value = state.email.value,
                onValueChange = { onEvent(SignInEvent.EmailChanged(it)) },
            )
            LabelledField(
                label = stringResource(Res.string.bl_admin_password),
                value = state.password.value,
                onValueChange = { onEvent(SignInEvent.PasswordChanged(it)) },
                password = true,
                // The rejection sits under the password, not under the form: it is
                // the password that was wrong, and the countdown is about it.
                error = state.error?.resolve(),
            )

            PillButton(
                text = stringResource(Res.string.bl_admin_sign_in),
                onClick = { onEvent(SignInEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
            TextLink(
                text = stringResource(Res.string.bl_admin_forgot),
                // Nothing behind it yet. It is in the design and in the markup so
                // the layout is right; wiring it needs a password-reset flow that
                // does not exist, and a link that silently does nothing would be
                // worse than one that is obviously next.
                onClick = {},
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
