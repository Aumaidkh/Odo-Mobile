package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcLockFilled
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import com.hopcape.odo.feature.profile.presentation.RowDivider
import com.hopcape.odo.feature.profile.presentation.SectionLabel
import com.hopcape.odo.feature.profile.presentation.SettingsGroup
import com.hopcape.odo.feature.profile.presentation.SettingsRow
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_pv_analytics
import com.hopcape.odo.feature.profile.resources.pf_pv_analytics_sub
import com.hopcape.odo.feature.profile.resources.pf_pv_delete_account
import com.hopcape.odo.feature.profile.resources.pf_pv_device_access
import com.hopcape.odo.feature.profile.resources.pf_pv_device_lede
import com.hopcape.odo.feature.profile.resources.pf_pv_policy
import com.hopcape.odo.feature.profile.resources.pf_pv_routes
import com.hopcape.odo.feature.profile.resources.pf_pv_routes_off
import com.hopcape.odo.feature.profile.resources.pf_pv_routes_on
import com.hopcape.odo.feature.profile.resources.pf_pv_share_prices
import com.hopcape.odo.feature.profile.resources.pf_pv_share_prices_sub
import com.hopcape.odo.feature.profile.resources.pf_pv_title
import com.hopcape.odo.feature.profile.resources.pf_pv_usage
import org.jetbrains.compose.resources.stringResource

/**
 * What Odo can reach, and what it keeps.
 *
 * Two halves that look alike and are not. The top is Android's — read-only, because the OS
 * owns those switches. The bottom is Odo's, and every switch there writes as it moves, with
 * no Save button: a privacy choice that only lived on screen would be the worst kind to lose.
 *
 * The lede sits over the device-access group alone. "Everything here is off unless you turned
 * it on" is true of permissions and false of the section below, where two of three ship on —
 * so it is not allowed to stand as a claim about the whole screen.
 */
@Composable
internal fun PrivacyScreen(
    state: PrivacyUiState,
    onEvent: (PrivacyEvent) -> Unit,
    onBack: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.pf_pv_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            state.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.danger,
                )
            }

            OdoText(
                stringResource(Res.string.pf_pv_device_lede),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            SectionLabel(stringResource(Res.string.pf_pv_device_access))
            DeviceAccessSection()

            SectionLabel(stringResource(Res.string.pf_pv_usage))
            SettingsGroup {
                OdoSwitchRow(
                    label = stringResource(Res.string.pf_pv_share_prices),
                    checked = state.sharePrices,
                    onCheckedChange = { onEvent(PrivacyEvent.SharePricesToggled(it)) },
                    supporting = stringResource(Res.string.pf_pv_share_prices_sub),
                    modifier = Modifier.testTag(ProfileTestTags.PRIVACY_SHARE_PRICES),
                )
                RowDivider()
                OdoSwitchRow(
                    label = stringResource(Res.string.pf_pv_routes),
                    checked = state.keepTripRoutes,
                    onCheckedChange = { onEvent(PrivacyEvent.KeepTripRoutesToggled(it)) },
                    // The supporting line states what is true now rather than what the
                    // switch would do. An owner reading "only distance is stored" under a
                    // switch that is on would have it exactly backwards.
                    supporting = if (state.keepTripRoutes) {
                        stringResource(Res.string.pf_pv_routes_on)
                    } else {
                        stringResource(Res.string.pf_pv_routes_off)
                    },
                    modifier = Modifier.testTag(ProfileTestTags.PRIVACY_KEEP_ROUTES),
                )
                RowDivider()
                OdoSwitchRow(
                    label = stringResource(Res.string.pf_pv_analytics),
                    checked = state.usageAnalytics,
                    onCheckedChange = { onEvent(PrivacyEvent.UsageAnalyticsToggled(it)) },
                    supporting = stringResource(Res.string.pf_pv_analytics_sub),
                    modifier = Modifier.testTag(ProfileTestTags.PRIVACY_USAGE_ANALYTICS),
                )
            }

            Spacer(Modifier.height(OdoTheme.spacing.md))
            SettingsGroup {
                SettingsRow(
                    icon = IcLockFilled,
                    title = stringResource(Res.string.pf_pv_policy),
                    onClick = onPrivacyPolicy,
                )
                RowDivider()
                SettingsRow(
                    icon = IcTrash,
                    title = stringResource(Res.string.pf_pv_delete_account),
                    onClick = onDeleteAccount,
                    danger = true,
                    testTag = ProfileTestTags.PRIVACY_DELETE_ACCOUNT,
                )
            }
        }
    }
}
