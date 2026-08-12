package com.hopcape.odo.feature.billscanner.presentation.document

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDateField
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
import com.hopcape.odo.core.designsystem.icons.IcImage
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.platform.file.StoredFileKind
import com.hopcape.odo.core.platform.file.StoredFileKinds
import com.hopcape.odo.core.platform.file.rememberStoredImage
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTestTags
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_cancel
import com.hopcape.odo.feature.billscanner.resources.bs_badge_pdf
import com.hopcape.odo.feature.billscanner.resources.bs_cd_document_file
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
                if (state.needsExpiry && state.expiresOn == null && !state.isReading) {
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
            // confirming it is what this screen is for. An uploaded PDF has no image to draw,
            // so it shows a glyph that still opens the file.
            state.photoKey?.let { key ->
                val isPdf = StoredFileKinds.of(key) == StoredFileKind.PDF
                OdoThumbnail(
                    image = rememberStoredImage(key),
                    contentDescription = stringResource(Res.string.bs_cd_document_file),
                    modifier = Modifier.fillMaxWidth().height(PHOTO_HEIGHT),
                    placeholderIcon = if (isPdf) IcPdf else IcImage,
                    badge = if (isPdf) stringResource(Res.string.bs_badge_pdf) else null,
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

/** The design system's date field, with this screen's own labels. */
@Composable
private fun DateField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoDateField(
        date = date,
        formatted = date?.let { formatDate(it) }.orEmpty(),
        placeholder = stringResource(Res.string.bs_doc_not_set),
        confirmLabel = stringResource(Res.string.bs_ok),
        cancelLabel = stringResource(Res.string.bs_cancel),
        onDateChange = onDateChange,
        modifier = modifier,
    )
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
