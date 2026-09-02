package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_config
import com.hopcape.odo.feature.profile.resources.pf_developer_options_title
import com.hopcape.odo.feature.profile.resources.pf_logs
import org.jetbrains.compose.resources.stringResource

/**
 * The developer-tools hub: two rows, each opening its own screen.
 *
 * Debug builds only — the row on the account home that opens this is behind
 * `BuildInfo.isDebug`, and this screen's own route stays registered in every build, the
 * same shape `Profile.ConfigOverrides` already used before it moved here: unreachable in
 * release, not removed.
 */
@Composable
internal fun DeveloperOptionsScreen(
    onConfigOverrides: () -> Unit,
    onLogs: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.pf_developer_options_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(OdoTheme.spacing.md)) {
            SettingsGroup {
                SettingsRow(
                    icon = IcInfo,
                    title = stringResource(Res.string.pf_config),
                    onClick = onConfigOverrides,
                )
                RowDivider()
                SettingsRow(
                    icon = IcJournal,
                    title = stringResource(Res.string.pf_logs),
                    onClick = onLogs,
                )
            }
        }
    }
}
