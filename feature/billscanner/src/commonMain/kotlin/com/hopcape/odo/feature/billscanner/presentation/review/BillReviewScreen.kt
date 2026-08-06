package com.hopcape.odo.feature.billscanner.presentation.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoOdometerField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcPencil
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.platform.file.rememberStoredImage
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_cancel
import com.hopcape.odo.feature.billscanner.resources.bs_cd_captured_bill
import com.hopcape.odo.feature.billscanner.resources.bs_cd_edit
import com.hopcape.odo.feature.billscanner.resources.bs_doc_not_set
import com.hopcape.odo.feature.billscanner.resources.bs_ok
import com.hopcape.odo.feature.billscanner.resources.bs_review_check
import com.hopcape.odo.feature.billscanner.resources.bs_review_date
import com.hopcape.odo.feature.billscanner.resources.bs_review_extracted
import com.hopcape.odo.feature.billscanner.resources.bs_review_line_items
import com.hopcape.odo.feature.billscanner.resources.bs_review_blurry_note
import com.hopcape.odo.feature.billscanner.resources.bs_review_confidence_low
import com.hopcape.odo.feature.billscanner.resources.bs_review_low_confidence
import com.hopcape.odo.feature.billscanner.resources.bs_review_low_note
import com.hopcape.odo.feature.billscanner.resources.bs_review_odometer
import com.hopcape.odo.feature.billscanner.resources.bs_review_retake
import com.hopcape.odo.feature.billscanner.resources.bs_review_save
import com.hopcape.odo.feature.billscanner.resources.bs_review_title
import com.hopcape.odo.feature.billscanner.resources.bs_review_total
import com.hopcape.odo.feature.billscanner.resources.bs_review_workshop
import com.hopcape.odo.feature.billscanner.resources.bs_scan_reading
import com.hopcape.odo.feature.billscanner.resources.bs_unit_km
import com.hopcape.odo.feature.billscanner.resources.bs_unit_miles
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * The "Review details" screen — the confirmation step after the AI reads a bill.
 * Surfaces the extraction confidence, the editable header fields (workshop, date,
 * odometer), and the line items + total read from the bill, then offers the primary
 * "Save & check fairness" action or a retake.
 *
 * State-free by design: it renders [state] and forwards intents. Wiring the real
 * scan result + persistence + fairness check is M2.
 */
@Composable
internal fun BillReviewScreen(
    state: BillReviewUiState,
    onWorkshopChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onOdometerChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.bs_review_title),
        onBack = onBack,
        bottomBar = { ReviewBottomBar(state = state, onSave = onSave, onRetake = onRetake) },
    ) { padding ->
        // Nothing has been read yet. Without this the screen is an empty form with no sign
        // that anything is happening, and the owner starts typing over fields that are about
        // to be filled in.
        if (state.isReading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    OdoTheme.spacing.md,
                    Alignment.CenterVertically,
                ),
            ) {
                OdoLoadingIndicator()
                OdoText(stringResource(Res.string.bs_scan_reading), color = OdoTheme.colors.textDim)
            }
            return@OdoScreen
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            CapturedPhoto(storageKey = state.photoKey)

            ExtractedBanner(confidence = state.confidence, high = !state.flagged)

            // One caution at a time: a blurry capture is the more actionable fact (retake
            // beats squinting), so it wins over the generic check-the-lines note.
            when {
                state.photoBlurry -> CautionNote(stringResource(Res.string.bs_review_blurry_note))
                state.flagged -> CautionNote(stringResource(Res.string.bs_review_low_note))
            }

            LabeledField(stringResource(Res.string.bs_review_workshop)) {
                OdoInputField(
                    value = state.workshop,
                    onValueChange = onWorkshopChange,
                    trailingIcon = {
                        OdoIcon(
                            IcPencil,
                            contentDescription = stringResource(Res.string.bs_cd_edit),
                            tint = OdoTheme.colors.textDim,
                            size = OdoTheme.iconSizes.small,
                        )
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                LabeledField(stringResource(Res.string.bs_review_date), Modifier.weight(1f)) {
                    DateField(date = state.serviceDate, onDateChange = onDateChange)
                }
                LabeledField(stringResource(Res.string.bs_review_odometer), Modifier.weight(1f)) {
                    OdoOdometerField(
                        value = state.odometer,
                        onValueChange = onOdometerChange,
                        unit = state.odometerUnit,
                        kmLabel = stringResource(Res.string.bs_unit_km),
                        milesLabel = stringResource(Res.string.bs_unit_miles),
                    )
                }
            }

            LabeledField(stringResource(Res.string.bs_review_line_items)) {
                LineItemsCard(items = state.lineItems, total = state.total)
            }
        }
    }
}

/** A small dim caption above a field, matching the review-screen field layout. */
@Composable
private fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(label, style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        content()
    }
}

/**
 * The editable service-date field: a read-only [OdoInputField] showing the formatted
 * date with a tap overlay that opens a Material date picker (the extracted date can be
 * corrected on review, like every other field here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, onDateChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Box {
        OdoInputField(
            // Empty rather than today's date when the bill's date could not be read. A
            // plausible-looking default is the one thing a review step must not offer.
            value = date?.let { formatDate(it) } ?: stringResource(Res.string.bs_doc_not_set),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only field can't focus, so an overlay captures the tap.
        Box(Modifier.matchParentSize().clip(OdoTheme.shapes.field).clickable { showPicker = true })
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                OdoButton(
                    text = stringResource(Res.string.bs_ok),
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
                    text = stringResource(Res.string.bs_cancel),
                    onClick = { showPicker = false },
                    variant = OdoButtonVariant.Tertiary,
                )
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The photo the fields below were read from.
 *
 * Shown so the owner is checking the extraction against the bill rather than against memory.
 * Draws nothing at all when there is no photo or it cannot be read, because the fields are
 * still reviewable without it.
 */
@Composable
private fun CapturedPhoto(storageKey: String?) {
    val photo = rememberStoredImage(storageKey) ?: return
    Image(
        bitmap = photo,
        contentDescription = stringResource(Res.string.bs_cd_captured_bill),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(PHOTO_HEIGHT)
            .clip(OdoTheme.shapes.card),
    )
}

/** How much of the screen the bill photo takes before the fields start. */
private val PHOTO_HEIGHT = 180.dp

/**
 * The extraction-confidence banner. High confidence reads green; low confidence
 * reads amber — surfacing the score honestly rather than implying false precision.
 */
@Composable
private fun ExtractedBanner(confidence: Int, high: Boolean) {
    val tone = if (high) OdoTheme.colors.success else OdoTheme.colors.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.field)
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.45f), OdoTheme.shapes.field)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcCheck, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.small)
        OdoText(
            // The green banner carries the measured number. The amber one says LOW instead:
            // it also covers handwriting, where the score is not evidence of being right,
            // and a precise figure on a warning reads as trust the read has not earned.
            text = if (high) {
                stringResource(Res.string.bs_review_extracted, "$confidence%")
            } else {
                stringResource(
                    Res.string.bs_review_low_confidence,
                    stringResource(Res.string.bs_review_confidence_low),
                )
            },
            style = OdoTheme.typography.caption,
            color = tone,
        )
    }
}

/**
 * Caution note under the banner — the blur warning or the check-the-lines nudge,
 * whichever fact the extraction actually measured.
 */
@Composable
private fun CautionNote(text: String) {
    OdoCard(color = OdoTheme.colors.surface) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.medium)
            OdoText(
                text,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun LineItemsCard(items: List<BillLineItem>, total: Amount) {
    OdoCard(
        color = OdoTheme.colors.surface,
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEach { item ->
            LineRow(label = item.label, value = item.amount.formatRupees(), emphasized = false, needsCheck = item.needsCheck)
            HorizontalDivider(color = OdoTheme.colors.border)
        }
        LineRow(label = stringResource(Res.string.bs_review_total), value = total.formatRupees(), emphasized = true)
    }
}

@Composable
private fun LineRow(label: String, value: String, emphasized: Boolean, needsCheck: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OdoText(
                label,
                style = if (emphasized) OdoTheme.typography.heading else OdoTheme.typography.body,
            )
            if (needsCheck) CheckPill()
        }
        OdoText(
            value,
            style = if (emphasized) OdoTheme.typography.title else OdoTheme.typography.heading,
            color = if (needsCheck) OdoTheme.colors.warning else Color.Unspecified,
        )
    }
}

/** The amber "CHECK" pill marking a low-confidence line item to verify. */
@Composable
private fun CheckPill() {
    OdoText(
        stringResource(Res.string.bs_review_check),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.warning,
        modifier = Modifier
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.warning.copy(alpha = 0.15f))
            .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
    )
}

@Composable
private fun ReviewBottomBar(
    state: BillReviewUiState,
    onSave: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        // The extraction and the save both report here. Until this was wired the screen
        // swallowed every failure — including "scanning isn't available", which is what the
        // unconfigured extractor returns for every scan today.
        state.submission.error?.let { message ->
            OdoText(
                text = message.asString(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
            )
        }
        OdoButton(
            text = stringResource(Res.string.bs_review_save),
            onClick = onSave,
            // A bill with no date cannot be saved, so the button says so rather than failing
            // on a rule the owner cannot see.
            enabled = state.canSave,
            loading = state.submission.isInFlight,
            modifier = Modifier.fillMaxWidth(),
        )
        OdoText(
            stringResource(Res.string.bs_review_retake),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .clickable(onClick = onRetake)
                .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        )
    }
}

@OdoThemePreviews
@Composable
private fun BillReviewScreenPreview() = OdoPreview(padded = false) {
    BillReviewScreen(
        state = sampleBillReviewState(),
        onWorkshopChange = {},
        onDateChange = {},
        onOdometerChange = {},
        onSave = {},
        onRetake = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun BillReviewLowConfidencePreview() = OdoPreview(padded = false) {
    BillReviewScreen(
        state = sampleBillReviewLowConfidence(),
        onWorkshopChange = {},
        onDateChange = {},
        onOdometerChange = {},
        onSave = {},
        onRetake = {},
        onBack = {},
    )
}
