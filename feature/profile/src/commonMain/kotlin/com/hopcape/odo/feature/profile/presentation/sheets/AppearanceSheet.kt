package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.feature.profile.presentation.ProfileSheet
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_appear_dark
import com.hopcape.odo.feature.profile.resources.pf_appear_larger
import com.hopcape.odo.feature.profile.resources.pf_appear_larger_sub
import com.hopcape.odo.feature.profile.resources.pf_appear_light
import com.hopcape.odo.feature.profile.resources.pf_appear_system
import com.hopcape.odo.feature.profile.resources.pf_appearance
import com.hopcape.odo.feature.profile.resources.pf_done
import org.jetbrains.compose.resources.stringResource

private val PreviewDark = Color(0xFF1C1C1E)
private val PreviewLight = Color(0xFFF3EFE9)

/**
 * Appearance sheet: theme and text size.
 *
 * Each choice is stored as it is tapped and the app redraws behind the sheet, so "Done"
 * only closes it — there is nothing left to apply by then.
 */
@Composable
internal fun AppearanceSheetContent(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
    onDone: () -> Unit,
) {
    ProfileSheet {
        OdoText(stringResource(Res.string.pf_appearance), style = OdoTheme.typography.heading)
        state.error?.let { message ->
            OdoText(message.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            ThemeCard(stringResource(Res.string.pf_appear_dark), ThemePreference.DARK, state.theme) {
                onEvent(AppearanceEvent.ThemeChosen(ThemePreference.DARK))
            }
            ThemeCard(stringResource(Res.string.pf_appear_light), ThemePreference.LIGHT, state.theme) {
                onEvent(AppearanceEvent.ThemeChosen(ThemePreference.LIGHT))
            }
            ThemeCard(stringResource(Res.string.pf_appear_system), ThemePreference.SYSTEM, state.theme) {
                onEvent(AppearanceEvent.ThemeChosen(ThemePreference.SYSTEM))
            }
        }
        OdoDivider()
        OdoSwitchRow(
            label = stringResource(Res.string.pf_appear_larger),
            checked = state.largerText,
            onCheckedChange = { onEvent(AppearanceEvent.LargerTextToggled(it)) },
            supporting = stringResource(Res.string.pf_appear_larger_sub),
        )
        OdoButton(stringResource(Res.string.pf_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RowScope.ThemeCard(
    label: String,
    value: ThemePreference,
    selected: ThemePreference,
    onClick: () -> Unit,
) {
    val isSelected = value == selected
    Column(
        modifier = Modifier.weight(1f).testTag(AppearanceTestTags.themeCard(value)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(OdoTheme.shapes.field)
                .border(
                    BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) OdoTheme.colors.accent else OdoTheme.colors.border),
                    OdoTheme.shapes.field,
                ),
        ) {
            when (value) {
                ThemePreference.DARK -> Box(Modifier.fillMaxWidth().fillMaxHeight().background(PreviewDark))
                ThemePreference.LIGHT -> Box(Modifier.fillMaxWidth().fillMaxHeight().background(PreviewLight))
                ThemePreference.SYSTEM -> Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(PreviewDark))
                    Box(Modifier.weight(1f).fillMaxHeight().background(PreviewLight))
                }
            }
        }
        OdoText(label, style = OdoTheme.typography.label, color = if (isSelected) OdoTheme.colors.text else OdoTheme.colors.textDim)
    }
}

/**
 * The three cards say only "Dark" / "Light" / "System", and the profile row behind the
 * sheet says one of those words too.
 */
object AppearanceTestTags {
    fun themeCard(theme: ThemePreference): String = "profile_theme_${theme.name}"
}
