package com.hopcape.odo.feature.billscanner.presentation.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.OdoThumbnail
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.platform.file.rememberStoredImage
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTestTags
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_cancel
import com.hopcape.odo.feature.billscanner.resources.bs_cd_captured_bill
import com.hopcape.odo.feature.billscanner.resources.bs_doc_expiry
import com.hopcape.odo.feature.billscanner.resources.bs_doc_expiry_required
import com.hopcape.odo.feature.billscanner.resources.bs_doc_issued
import com.hopcape.odo.feature.billscanner.resources.bs_doc_name
import com.hopcape.odo.feature.billscanner.resources.bs_doc_not_set
import com.hopcape.odo.feature.billscanner.resources.bs_doc_save
import com.hopcape.odo.feature.billscanner.resources.bs_doc_title
import com.hopcape.odo.feature.billscanner.resources.bs_doc_type
import com.hopcape.odo.feature.billscanner.resources.bs_ok
import com.hopcape.odo.feature.billscanner.resources.bs_scan_reading
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * Confirms a scanned paper before it is filed in the vault.
 *
 * The expiry date is the field this screen is really about, so it is stated as required
 * rather than merely left empty: a document filed without one produces no reminder, which is
 * the single thing an owner adds it for.
 */
@Composable
internal fun DocumentReviewScreen(
    state: DocumentReviewUiState,
    onEvent: (DocumentReviewEvent) -> Unit,
    onOpenPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.bs_doc_title),
        onBack = { onEvent(DocumentReviewEvent.BackTapped) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OdoTheme.spacing.screenEdge,
                        vertical = OdoTheme.spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                state.submission.error?.let { message ->
                    OdoText(
                        text = message.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                    )
                }
                if (state.expiresOn == null && !state.isReading) {
                    OdoText(
                        text = stringResource(Res.string.bs_doc_expiry_required),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
                OdoButton(
                    text = stringResource(Res.string.bs_doc_save),
                    onClick = { onEvent(DocumentReviewEvent.SaveTapped) },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        if (state.isReading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
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
            // Tappable: at this height the expiry date on a policy is not readable, and
            // confirming it is what this screen is for.
            rememberStoredImage(state.photoKey)?.let { photo ->
                OdoThumbnail(
                    image = photo,
                    contentDescription = stringResource(Res.string.bs_cd_captured_bill),
                    modifier = Modifier.fillMaxWidth().height(PHOTO_HEIGHT),
                    onClick = onOpenPhoto,
                )
            }

            Labelled(stringResource(Res.string.bs_doc_type)) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    DocumentType.entries.chunked(TYPES_PER_ROW).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                        ) {
                            row.forEach { type ->
                                OdoChip(
                                    label = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    onClick = { onEvent(DocumentReviewEvent.TypeChanged(type)) },
                                    selected = type == state.type,
                                )
                            }
                        }
                    }
                }
            }

            Labelled(stringResource(Res.string.bs_doc_name)) {
                OdoInputField(
                    value = state.title,
                    onValueChange = { onEvent(DocumentReviewEvent.TitleChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Labelled(stringResource(Res.string.bs_doc_issued)) {
                DateField(
                    date = state.issuedOn,
                    onDateChange = { onEvent(DocumentReviewEvent.IssuedOnChanged(it)) },
                )
            }

            Labelled(stringResource(Res.string.bs_doc_expiry)) {
                DateField(
                    date = state.expiresOn,
                    onDateChange = { onEvent(DocumentReviewEvent.ExpiresOnChanged(it)) },
                    modifier = Modifier.testTag(BillScannerTestTags.DOCUMENT_EXPIRY_FIELD),
                )
            }
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(text = label, style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        content()
    }
}

/** A read-only field that opens a date picker. Empty when nothing was read and nothing typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier) {
        OdoInputField(
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

private val PHOTO_HEIGHT = 180.dp

/** Document types wrap onto rows of this many chips. */
private const val TYPES_PER_ROW = 3

@OdoThemePreviews
@Composable
private fun DocumentReviewScreenPreview() = OdoPreview(padded = false) {
    DocumentReviewScreen(
        state = DocumentReviewUiState(
            submission = Submission.Idle,
            type = DocumentType.INSURANCE,
            title = "SafeDrive comprehensive",
            expiresOn = LocalDate(2027, 3, 14),
        ),
        onEvent = {},
        onOpenPhoto = {},
    )
}
