package com.hopcape.odo.feature.servicelog.presentation.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoOdometerField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_amount_currency
import com.hopcape.odo.feature.servicelog.resources.sl_field_other_hint
import com.hopcape.odo.feature.servicelog.resources.sl_unit_km
import com.hopcape.odo.feature.servicelog.resources.sl_unit_miles
import com.hopcape.odo.feature.servicelog.resources.sl_attach_bill
import com.hopcape.odo.feature.servicelog.resources.sl_cancel
import com.hopcape.odo.feature.servicelog.resources.sl_cat_ac
import com.hopcape.odo.feature.servicelog.resources.sl_cat_add
import com.hopcape.odo.feature.servicelog.resources.sl_cat_battery
import com.hopcape.odo.feature.servicelog.resources.sl_cat_brakes
import com.hopcape.odo.feature.servicelog.resources.sl_cat_electrical
import com.hopcape.odo.feature.servicelog.resources.sl_cat_general_service
import com.hopcape.odo.feature.servicelog.resources.sl_cat_oil_change
import com.hopcape.odo.feature.servicelog.resources.sl_cat_other
import com.hopcape.odo.feature.servicelog.resources.sl_cat_suspension
import com.hopcape.odo.feature.servicelog.resources.sl_cat_tyres
import com.hopcape.odo.feature.servicelog.resources.sl_divider_or_details
import com.hopcape.odo.feature.servicelog.resources.sl_field_amount
import com.hopcape.odo.feature.servicelog.resources.sl_field_amount_hint
import com.hopcape.odo.feature.servicelog.resources.sl_field_date
import com.hopcape.odo.feature.servicelog.resources.sl_field_date_hint
import com.hopcape.odo.feature.servicelog.resources.sl_field_notes
import com.hopcape.odo.feature.servicelog.resources.sl_field_odometer
import com.hopcape.odo.feature.servicelog.resources.sl_field_odometer_hint
import com.hopcape.odo.feature.servicelog.resources.sl_field_workshop
import com.hopcape.odo.feature.servicelog.resources.sl_field_workshop_hint
import com.hopcape.odo.feature.servicelog.resources.sl_form_title_add
import com.hopcape.odo.feature.servicelog.resources.sl_form_title_edit
import com.hopcape.odo.feature.servicelog.resources.sl_ok
import com.hopcape.odo.feature.servicelog.resources.sl_optional
import com.hopcape.odo.feature.servicelog.resources.sl_save
import com.hopcape.odo.feature.servicelog.resources.sl_scan_cta_fastest
import com.hopcape.odo.feature.servicelog.resources.sl_scan_cta_subtitle
import com.hopcape.odo.feature.servicelog.resources.sl_scan_cta_title
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** The common "what was done" tags shown up front; "+ Add" reveals the rest. */
private val CommonCategories = listOf(ServiceCategory.OIL_CHANGE, ServiceCategory.BRAKES, ServiceCategory.TYRES)

/**
 * The add / edit service form — one screen for both modes ([ServiceLogFormUiState.isEditing]).
 * A "scan the bill" shortcut sits above the manual fields (Workshop, Date, Odometer, the
 * "what was done" chips, Amount) and an optional attach-bill slot; Save is pinned to the
 * bottom. Stateless: the route host owns the state + navigation.
 */
@Composable
internal fun ServiceLogFormScreen(
    state: ServiceLogFormUiState,
    onWorkshopChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onOdometerChange: (String) -> Unit,
    onOdometerUnitToggle: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryToggle: (ServiceCategory) -> Unit,
    onNotesChange: (String) -> Unit,
    onScanBill: () -> Unit,
    onAttachBill: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        title = stringResource(if (state.isEditing) Res.string.sl_form_title_edit else Res.string.sl_form_title_add),
        onBack = onClose,
        modifier = modifier,
        bottomBar = { SaveBar(state, onSave) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            ScanCtaCard(onScanBill)
            OrDivider()
            OdoInputField(
                value = state.workshop.value.orEmpty(),
                onValueChange = onWorkshopChange,
                label = stringResource(Res.string.sl_field_workshop),
                placeholder = stringResource(Res.string.sl_field_workshop_hint),
                errorText = state.workshop.error?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                DateField(state.date.value, onDateChange, state.date.error?.asString(), Modifier.weight(1f))
                OdoOdometerField(
                    value = state.odometer.value.orEmpty(),
                    onValueChange = onOdometerChange,
                    unit = state.odometerUnit,
                    onUnitToggle = onOdometerUnitToggle,
                    kmLabel = stringResource(Res.string.sl_unit_km),
                    milesLabel = stringResource(Res.string.sl_unit_miles),
                    label = stringResource(Res.string.sl_field_odometer),
                    placeholder = stringResource(Res.string.sl_field_odometer_hint),
                    errorText = state.odometer.error?.asString(),
                    modifier = Modifier.weight(1f),
                )
            }
            CategorySection(state.categories, onCategoryToggle, state.notes.value.orEmpty(), onNotesChange)
            OdoInputField(
                value = state.amount.value.orEmpty(),
                onValueChange = onAmountChange,
                label = stringResource(Res.string.sl_field_amount),
                placeholder = stringResource(Res.string.sl_field_amount_hint),
                errorText = state.amount.error?.asString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = {
                    OdoText(stringResource(Res.string.sl_amount_currency), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AttachBillCard(onAttachBill)
        }
    }
}

/** The accent "scan the bill instead" shortcut into the AI Bill Scanner (M2). */
@Composable
private fun ScanCtaCard(onScanBill: () -> Unit) {
    OdoCard(
        onClick = onScanBill,
        color = OdoTheme.colors.accent.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.5f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcCamera, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoText(stringResource(Res.string.sl_scan_cta_title), style = OdoTheme.typography.heading)
                    OdoBadge(stringResource(Res.string.sl_scan_cta_fastest), tone = OdoBadgeTone.Accent)
                }
                OdoText(
                    stringResource(Res.string.sl_scan_cta_subtitle),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OdoDivider(Modifier.weight(1f))
        OdoText(stringResource(Res.string.sl_divider_or_details), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        OdoDivider(Modifier.weight(1f))
    }
}

/** A read-only date field that opens a date picker on tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, onDateChange: (LocalDate) -> Unit, error: String?, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier) {
        OdoInputField(
            value = date?.let { formatDate(it) }.orEmpty(),
            onValueChange = {},
            label = stringResource(Res.string.sl_field_date),
            placeholder = stringResource(Res.string.sl_field_date_hint),
            readOnly = true,
            errorText = error,
            modifier = Modifier.fillMaxWidth(),
        )
        // Read-only field can't focus, so an overlay captures the tap.
        Box(Modifier.matchParentSize().clip(OdoTheme.shapes.field).clickable { showPicker = true })
    }
    if (showPicker) {
        val initialMillis = date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                OdoButton(
                    text = stringResource(Res.string.sl_ok),
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                        }
                        showPicker = false
                    },
                    variant = OdoButtonVariant.Tertiary,
                )
            },
            dismissButton = {
                OdoButton(
                    text = stringResource(Res.string.sl_cancel),
                    onClick = { showPicker = false },
                    variant = OdoButtonVariant.Tertiary,
                )
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun CategorySection(
    selected: Set<ServiceCategory>,
    onToggle: (ServiceCategory) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(stringResource(Res.string.sl_field_notes), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            val categories = if (expanded) ServiceCategory.entries else CommonCategories
            categories.forEach { category ->
                OdoChip(
                    label = categoryLabel(category),
                    selected = category in selected,
                    onClick = { onToggle(category) },
                )
            }
            if (!expanded) {
                OdoChip(
                    label = stringResource(Res.string.sl_cat_add),
                    selected = false,
                    onClick = { expanded = true },
                )
            }
        }
        // "Other" opens a free-text box to say exactly what was done — animated in/out.
        AnimatedVisibility(visible = ServiceCategory.OTHER in selected) {
            OdoInputField(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = stringResource(Res.string.sl_field_other_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/** The dashed, optional "attach a bill photo" slot (verifies the entry once a bill lands). */
@Composable
private fun AttachBillCard(onClick: () -> Unit) {
    val border = OdoTheme.colors.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.card)
            .drawBehind {
                drawRoundRect(
                    color = border,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcJournal, contentDescription = null, tint = OdoTheme.colors.textMuted, size = OdoTheme.iconSizes.small)
        OdoText(stringResource(Res.string.sl_attach_bill), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
        OdoText(stringResource(Res.string.sl_optional), style = OdoTheme.typography.body, color = OdoTheme.colors.textMuted)
    }
}

@Composable
private fun SaveBar(state: ServiceLogFormUiState, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        state.submitError?.let {
            OdoText(it.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.danger)
        }
        OdoButton(
            text = stringResource(Res.string.sl_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
            loading = state.isSubmitting,
        )
    }
}

@Composable
private fun categoryLabel(category: ServiceCategory): String = stringResource(
    when (category) {
        ServiceCategory.OIL_CHANGE -> Res.string.sl_cat_oil_change
        ServiceCategory.BRAKES -> Res.string.sl_cat_brakes
        ServiceCategory.TYRES -> Res.string.sl_cat_tyres
        ServiceCategory.AC -> Res.string.sl_cat_ac
        ServiceCategory.BATTERY -> Res.string.sl_cat_battery
        ServiceCategory.SUSPENSION -> Res.string.sl_cat_suspension
        ServiceCategory.ELECTRICAL -> Res.string.sl_cat_electrical
        ServiceCategory.GENERAL_SERVICE -> Res.string.sl_cat_general_service
        ServiceCategory.OTHER -> Res.string.sl_cat_other
    },
)
