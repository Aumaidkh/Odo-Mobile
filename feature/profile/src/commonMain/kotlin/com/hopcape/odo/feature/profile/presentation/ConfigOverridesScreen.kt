package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.config.ConfigValueSource
import com.hopcape.odo.core.config.ResolvedConfigValue
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_config_empty
import com.hopcape.odo.feature.profile.resources.pf_config_owner
import com.hopcape.odo.feature.profile.resources.pf_config_reset
import com.hopcape.odo.feature.profile.resources.pf_config_reset_all
import com.hopcape.odo.feature.profile.resources.pf_config_source_default
import com.hopcape.odo.feature.profile.resources.pf_config_source_override
import com.hopcape.odo.feature.profile.resources.pf_config_source_remote
import com.hopcape.odo.feature.profile.resources.pf_config_title
import com.hopcape.odo.feature.profile.resources.pf_config_value_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Every registered config key, what it currently answers, and **which step of the
 * resolution order answered** — an override, the backend, or the compiled default.
 *
 * Showing the source is the point of the screen. "The flag is off" and "the flag is off
 * because the console never set it and this is the compiled default" are different bugs,
 * and from a device there is otherwise no way to tell them apart.
 *
 * Debug builds only. The route stays registered in every build and the row that opens it
 * does not, which is the same shape the refuel routes use: unreachable, not removed.
 */
@Composable
internal fun ConfigOverridesScreen(
    keys: List<ResolvedConfigValue>,
    editable: Boolean,
    onSet: (key: String, raw: String) -> Unit,
    onClear: (key: String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.pf_config_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        if (keys.isEmpty()) {
            OdoText(
                stringResource(Res.string.pf_config_empty),
                modifier = Modifier.padding(padding).padding(OdoTheme.spacing.md),
            )
            return@OdoScreen
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(OdoTheme.spacing.md),
        ) {
            items(keys, key = { it.key.key }) { resolved ->
                ConfigKeyRow(resolved, editable, onSet, onClear)
                Spacer(Modifier.height(OdoTheme.spacing.md))
            }
            if (editable) {
                item {
                    OdoButton(
                        text = stringResource(Res.string.pf_config_reset_all),
                        onClick = onClearAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigKeyRow(
    resolved: ResolvedConfigValue,
    editable: Boolean,
    onSet: (key: String, raw: String) -> Unit,
    onClear: (key: String) -> Unit,
) {
    // Keyed on the resolved value so a rejected edit snaps back to what actually won,
    // rather than leaving the field showing a value nothing is using.
    var draft by rememberSaveable(resolved.key.key, resolved.value) { mutableStateOf(resolved.value) }
    val sourceLabel = when (resolved.source) {
        ConfigValueSource.OVERRIDE -> stringResource(Res.string.pf_config_source_override)
        ConfigValueSource.REMOTE -> stringResource(Res.string.pf_config_source_remote)
        ConfigValueSource.DEFAULT -> stringResource(Res.string.pf_config_source_default)
    }

    Column {
        OdoText(resolved.key.key, style = OdoTheme.typography.heading)
        OdoText(
            "$sourceLabel · ${resolved.key.type.name.lowercase()}",
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        OdoText(
            resolved.key.why,
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        OdoText(
            stringResource(Res.string.pf_config_owner, resolved.key.owner),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        if (editable) {
            OdoInputField(
                value = draft,
                onValueChange = { draft = it },
                label = stringResource(Res.string.pf_config_value_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                OdoButton(
                    text = stringResource(Res.string.pf_config_reset),
                    onClick = { onClear(resolved.key.key) },
                )
            }
            LaunchedCommit(draft, resolved) { onSet(resolved.key.key, draft) }
        } else {
            OdoText(resolved.value, style = OdoTheme.typography.body)
        }
    }
}

/**
 * Commits an edit once the field settles, rather than on every keystroke: a partially typed
 * number is not a value, and writing it would make the row flicker between the draft and
 * whatever the resolver fell back to.
 */
@Composable
private fun LaunchedCommit(draft: String, resolved: ResolvedConfigValue, commit: () -> Unit) {
    val current = remember(resolved.value) { resolved.value }
    LaunchedEffect(draft) {
        if (draft == current) return@LaunchedEffect
        delay(COMMIT_DELAY_MILLIS)
        commit()
    }
}

private const val COMMIT_DELAY_MILLIS = 600L
