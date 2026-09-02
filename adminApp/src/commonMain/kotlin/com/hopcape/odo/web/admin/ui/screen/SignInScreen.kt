package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.presentation.signin.SignInEvent
import com.hopcape.odo.web.admin.presentation.signin.SignInUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_shell_wordmark
import com.hopcape.odo.web.admin.resources.ad_shell_wordmark_sub
import com.hopcape.odo.web.admin.resources.ad_signin_busy
import com.hopcape.odo.web.admin.resources.ad_signin_email
import com.hopcape.odo.web.admin.resources.ad_signin_password
import com.hopcape.odo.web.admin.resources.ad_signin_submit
import com.hopcape.odo.web.admin.resources.ad_signin_subtitle
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * One card, centred, with nothing else on the page.
 *
 * No "forgot password" and no "create account", and neither is an omission: both
 * are Firebase console operations for a staff account — there is no self-serve
 * path into this panel by design, and a link that leads nowhere is worse than none.
 *
 * **Every colour here is explicit.** Nothing wraps this in a Material `Surface`, so
 * `LocalContentColor` is black — a `Text` that does not name its colour renders
 * black on black and simply is not there. That is how the title went missing on the
 * first deploy, and it is why this screen is built from the panel's own components
 * rather than Material's.
 */
@Composable
fun SignInScreen(state: SignInUiState, onEvent: (SignInEvent) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(AdminTokens.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.width(380.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(Res.string.ad_shell_wordmark),
                        style = AdminType.wordmark,
                        color = AdminTokens.text,
                    )
                    Text(
                        stringResource(Res.string.ad_shell_wordmark_sub),
                        style = AdminType.micro,
                        color = AdminTokens.textFaint,
                    )
                }

                Text(
                    stringResource(Res.string.ad_signin_subtitle),
                    style = AdminType.body,
                    color = AdminTokens.textFaint,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                Column {
                    FieldLabel(stringResource(Res.string.ad_signin_email).uppercase())
                    AdminField(
                        value = state.email.value,
                        onValueChange = { onEvent(SignInEvent.EmailChanged(it)) },
                        placeholder = stringResource(Res.string.ad_signin_email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    )
                }

                Column {
                    FieldLabel(stringResource(Res.string.ad_signin_password).uppercase())
                    AdminField(
                        value = state.password.value,
                        onValueChange = { onEvent(SignInEvent.PasswordChanged(it)) },
                        placeholder = stringResource(Res.string.ad_signin_password),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        masked = true,
                    )
                }

                // Under the fields, where the design of every other sign-in in this
                // codebase puts it.
                state.error?.let { error ->
                    StatusText(error.resolve(), AdminTokens.danger)
                }

                PrimaryAction(
                    label = if (state.busy) {
                        stringResource(Res.string.ad_signin_busy)
                    } else {
                        stringResource(Res.string.ad_signin_submit)
                    },
                    onClick = { onEvent(SignInEvent.Submit) },
                    enabled = state.canSubmit,
                )
            }
        }
    }
}
