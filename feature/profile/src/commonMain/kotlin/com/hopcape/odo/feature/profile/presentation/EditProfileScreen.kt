package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoDropdownField
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_close
import com.hopcape.odo.feature.profile.resources.pf_change_photo
import com.hopcape.odo.feature.profile.resources.pf_city
import com.hopcape.odo.feature.profile.resources.pf_city_note
import com.hopcape.odo.feature.profile.resources.pf_delete
import com.hopcape.odo.feature.profile.resources.pf_edit_title
import com.hopcape.odo.feature.profile.resources.pf_email
import com.hopcape.odo.feature.profile.resources.pf_email_hint
import com.hopcape.odo.feature.profile.resources.pf_full_name
import com.hopcape.odo.feature.profile.resources.pf_mobile
import com.hopcape.odo.feature.profile.resources.pf_mobile_note
import com.hopcape.odo.feature.profile.resources.pf_save
import com.hopcape.odo.feature.profile.resources.pf_verified
import org.jetbrains.compose.resources.stringResource

/**
 * Edit-profile full screen ([com.hopcape.odo.core.navigation.OdoDestination.Profile.Edit]).
 * Holds its form state (sample-seeded); [onSave] / [onClose] pop back, [onDelete] is the
 * destructive account action.
 */
@Composable
internal fun EditProfileScreen(onClose: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf("Rahul Deshmukh") }
    var email by remember { mutableStateOf("") }
    val cities = listOf("Pune", "Mumbai", "Delhi", "Bengaluru", "Chennai", "Hyderabad")
    var city by remember { mutableStateOf<String?>("Pune") }

    OdoScreen(
        topBar = { CloseTopBar(stringResource(Res.string.pf_edit_title), stringResource(Res.string.pf_cd_close), onClose) },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.screenEdge).padding(vertical = OdoTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                OdoButton(stringResource(Res.string.pf_save), onClick = onSave, modifier = Modifier.fillMaxWidth())
                OdoText(
                    stringResource(Res.string.pf_delete),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(vertical = OdoTheme.spacing.xs),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar("R", size = 88.dp)
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(OdoTheme.colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        OdoIcon(IcCamera, contentDescription = null, tint = OdoTheme.colors.onAccent, size = OdoTheme.iconSizes.small)
                    }
                }
                OdoText(stringResource(Res.string.pf_change_photo), style = OdoTheme.typography.label, color = OdoTheme.colors.accent, modifier = Modifier.clickable(onClick = {}))
            }
            OdoInputField(value = name, onValueChange = { name = it }, label = stringResource(Res.string.pf_full_name))
            OdoInputField(
                value = "+91 98765 43210",
                onValueChange = {},
                readOnly = true,
                label = stringResource(Res.string.pf_mobile),
                helperText = stringResource(Res.string.pf_mobile_note),
                trailingIcon = {
                    OdoBadge(
                        stringResource(Res.string.pf_verified),
                        tone = OdoBadgeTone.Accent,
                        leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
                    )
                },
            )
            OdoInputField(value = email, onValueChange = { email = it }, label = stringResource(Res.string.pf_email), placeholder = stringResource(Res.string.pf_email_hint))
            OdoDropdownField(selected = city, options = cities, onSelect = { city = it }, label = stringResource(Res.string.pf_city))
            OdoText(stringResource(Res.string.pf_city_note), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}
