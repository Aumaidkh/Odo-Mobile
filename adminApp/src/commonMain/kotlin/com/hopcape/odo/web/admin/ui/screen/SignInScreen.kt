package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.presentation.signin.SignInEvent
import com.hopcape.odo.web.admin.presentation.signin.SignInUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_signin_busy
import com.hopcape.odo.web.admin.resources.ad_signin_email
import com.hopcape.odo.web.admin.resources.ad_signin_password
import com.hopcape.odo.web.admin.resources.ad_signin_submit
import com.hopcape.odo.web.admin.resources.ad_signin_subtitle
import com.hopcape.odo.web.admin.resources.ad_signin_title
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * One card, centred, with nothing else on the page.
 *
 * No "forgot password" and no "create account", and neither is an omission. Both
 * are Firebase console operations for a staff account — there is no self-serve
 * path into this panel by design, and offering a link that leads nowhere is worse
 * than offering none.
 */
@Composable
fun SignInScreen(state: SignInUiState, onEvent: (SignInEvent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.ad_signin_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.ad_signin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                value = state.email.value,
                onValueChange = { onEvent(SignInEvent.EmailChanged(it)) },
                label = { Text(stringResource(Res.string.ad_signin_email)) },
                singleLine = true,
                enabled = !state.busy,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.password.value,
                onValueChange = { onEvent(SignInEvent.PasswordChanged(it)) },
                label = { Text(stringResource(Res.string.ad_signin_password)) },
                singleLine = true,
                enabled = !state.busy,
                isError = state.error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                // Enter submits. Reaching for the mouse to sign in is the kind of
                // small friction that this page will impose several times a day.
                keyboardActions = KeyboardActions(onGo = { onEvent(SignInEvent.Submit) }),
                modifier = Modifier.fillMaxWidth(),
            )

            // Under the password field, where the design of every other sign-in in
            // this codebase puts it.
            state.error?.let { error ->
                Text(
                    error.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { onEvent(SignInEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(stringResource(Res.string.ad_signin_busy))
                } else {
                    Text(stringResource(Res.string.ad_signin_submit))
                }
            }
        }
    }
}
