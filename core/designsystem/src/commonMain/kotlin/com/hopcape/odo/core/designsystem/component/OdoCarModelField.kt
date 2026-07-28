package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronDown
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcMagnifier
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlinx.coroutines.launch

/**
 * One car **model** row in the [OdoCarModelField] picker: a stable [id], the model
 * [name] ("City"), the trim [variant] ("VX CVT"), and an optional [subtitle] line.
 *
 * [variant] is part of the identity of what the owner drives — it moves service
 * prices — so it rides along with the model rather than being a second field. It
 * renders as trailing text on the collapsed field and beside the name in the sheet;
 * pass `null` when the catalog has no trim for that model.
 */
@Immutable
data class OdoCarModel(
    val id: String,
    val name: String,
    val variant: String? = null,
    val subtitle: String? = null,
)

/**
 * The **car model** picker — a collapsed field showing the chosen model with its trim,
 * which opens a searchable bottom sheet of models. The sibling [OdoCarMakeField] chooses
 * the brand; this one narrows it to the exact car, which is what price benchmarks key off.
 *
 * Typing filters [models] by name **or** variant (case-insensitive substring) into a
 * "matches" section with the matched span emphasised; with no query the sheet lists every
 * model. Tapping a row commits immediately and closes the sheet — no confirm button.
 *
 * Copy-free: every string is a parameter. Section labels render in the tracked-caps
 * caption style, so pass them already uppercased if that's the intent.
 *
 * ```
 * OdoCarModelField(
 *     selected = form.model,
 *     models = state.models,               // for the chosen make
 *     onSelect = { onEvent(ModelChanged(it)) },
 *     title = stringResource(Res.string.onb_model_sheet_title),
 *     subtitle = stringResource(Res.string.onb_model_sheet_subtitle, make.name),
 *     searchPlaceholder = stringResource(Res.string.onb_model_search),
 *     matchCountLabel = { stringResource(Res.string.onb_match_count, it) },
 *     allSectionLabel = stringResource(Res.string.onb_model_all),
 *     emptyResultsText = stringResource(Res.string.onb_model_empty),
 *     closeContentDescription = stringResource(Res.string.onb_cd_close),
 * )
 * ```
 *
 * @param matchCountLabel renders the match-count eyebrow for a result count ("2 MATCHES").
 * @param errorText when non-null, puts the field in its danger state and shows below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoCarModelField(
    selected: OdoCarModel?,
    models: List<OdoCarModel>,
    onSelect: (OdoCarModel) -> Unit,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    matchCountLabel: (Int) -> String,
    allSectionLabel: String,
    emptyResultsText: String,
    closeContentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val isError = errorText != null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        if (label != null) {
            OdoText(
                text = label,
                style = OdoTheme.typography.label,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
            )
        }
        CollapsedModelField(
            model = selected,
            placeholder = placeholder,
            isError = isError,
            enabled = enabled,
            onClick = { open = true },
        )
        // Error replaces helper — never both, matching OdoInputField.
        val supporting = errorText ?: helperText
        if (supporting != null) {
            OdoText(
                text = supporting,
                style = OdoTheme.typography.bodySmall,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
            )
        }
    }

    if (open) {
        // Animate the sheet out before dropping it, so a selection doesn't make the
        // sheet vanish mid-gesture.
        val dismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { open = false } }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = OdoTheme.colors.surface,
        ) {
            CarModelSheet(
                selected = selected,
                models = models,
                title = title,
                subtitle = subtitle,
                searchPlaceholder = searchPlaceholder,
                matchCountLabel = matchCountLabel,
                allSectionLabel = allSectionLabel,
                emptyResultsText = emptyResultsText,
                closeContentDescription = closeContentDescription,
                onSelect = { model ->
                    onSelect(model)
                    dismiss()
                },
                onClose = { dismiss() },
            )
        }
    }
}

/* ------------------------------ Collapsed field ------------------------------ */

@Composable
private fun CollapsedModelField(
    model: OdoCarModel?,
    placeholder: String?,
    isError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = OdoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .clip(OdoTheme.shapes.field)
            .background(colors.surface)
            .border(1.dp, if (isError) colors.danger else colors.border, OdoTheme.shapes.field)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoText(
            text = model?.name ?: placeholder.orEmpty(),
            style = OdoTheme.typography.heading,
            color = when {
                model == null -> colors.textMuted
                enabled -> colors.text
                else -> colors.textMuted
            },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        // The trim rides quietly beside the model — present, never competing with it.
        if (model?.variant != null) {
            OdoText(
                text = model.variant,
                style = OdoTheme.typography.label,
                color = colors.textDim,
                maxLines = 1,
            )
        }
        DownChevron()
    }
}

/** Down-chevron drawn in-house (no Material-icons dep), matching [OdoCarMakeField]. */
@Composable
private fun DownChevron() {
    OdoIcon(
        imageVector = IcChevronDown,
        contentDescription = null,
        tint = OdoTheme.colors.textDim,
        size = OdoTheme.iconSizes.medium,
    )
}

/* ------------------------------ Sheet ------------------------------ */

@Composable
private fun CarModelSheet(
    selected: OdoCarModel?,
    models: List<OdoCarModel>,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    matchCountLabel: (Int) -> String,
    allSectionLabel: String,
    emptyResultsText: String,
    closeContentDescription: String,
    onSelect: (OdoCarModel) -> Unit,
    onClose: () -> Unit,
) {
    val colors = OdoTheme.colors
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty()

    // Trim is searchable too: an owner who knows their car as "VX CVT" finds it that way.
    val rows = if (searching) {
        models.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.variant?.contains(trimmed, ignoreCase = true) == true
        }
    } else {
        models
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoText(title, style = OdoTheme.typography.title, color = colors.text)
                OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = colors.textDim)
            }
            OdoIconButton(
                imageVector = IcClose,
                contentDescription = closeContentDescription,
                onClick = onClose,
                tint = colors.textDim,
                size = OdoTheme.iconSizes.medium,
            )
        }

        OdoInputField(
            value = query,
            onValueChange = { query = it },
            placeholder = searchPlaceholder,
            singleLine = true,
            leadingIcon = {
                OdoIcon(
                    IcMagnifier,
                    contentDescription = null,
                    tint = colors.textDim,
                    size = OdoTheme.iconSizes.medium,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = SheetListMaxHeight),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            item(key = "header") {
                SectionHeader(if (searching) matchCountLabel(rows.size) else allSectionLabel)
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    OdoText(
                        emptyResultsText,
                        style = OdoTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(vertical = OdoTheme.spacing.xs),
                    )
                }
            } else {
                items(rows, key = { it.id }) { model ->
                    ModelRow(
                        model = model,
                        selected = model.id == selected?.id,
                        query = trimmed,
                        onClick = { onSelect(model) },
                    )
                }
            }
        }
    }
}

/** A tracked-caps section eyebrow ("2 MATCHES", "ALL MODELS"). */
@Composable
private fun SectionHeader(text: String) {
    OdoText(
        text = text,
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        modifier = Modifier.padding(top = OdoTheme.spacing.xs),
    )
}

/** One model row: name (matched span emphasised) + trim/subtitle, and a check when chosen. */
@Composable
private fun ModelRow(
    model: OdoCarModel,
    selected: Boolean,
    query: String,
    onClick: () -> Unit,
) {
    val colors = OdoTheme.colors
    val shape = OdoTheme.shapes.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = SelectedFill) else colors.surfaceRaised)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.accent else colors.border,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(
                text = highlightedName(model.name, query, colors.text, colors.textDim),
                style = OdoTheme.typography.heading,
                color = colors.text,
                maxLines = 1,
            )
            val support = listOfNotNull(model.variant, model.subtitle).joinToString(" · ")
            if (support.isNotEmpty()) {
                OdoText(support, style = OdoTheme.typography.bodySmall, color = colors.textDim, maxLines = 1)
            }
        }
        if (selected) {
            OdoIcon(
                IcCheck,
                contentDescription = null,
                tint = colors.accent,
                size = OdoTheme.iconSizes.large,
            )
        }
    }
}

/**
 * Emphasises the matched span of [name] against [query] — matched characters in [matched]
 * ink, the rest in [rest] — so the sheet echoes what was typed ("**Cit**y"). With a blank
 * [query] the whole name renders in [matched].
 */
private fun highlightedName(name: String, query: String, matched: Color, rest: Color): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(name)
    val start = name.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(name)
    val end = start + query.length
    return buildAnnotatedString {
        if (start > 0) withStyle(SpanStyle(color = rest)) { append(name.substring(0, start)) }
        withStyle(SpanStyle(color = matched, fontWeight = FontWeight.SemiBold)) {
            append(name.substring(start, end))
        }
        if (end < name.length) withStyle(SpanStyle(color = rest)) { append(name.substring(end)) }
    }
}

private val SheetListMaxHeight = 420.dp

/** Accent tint for the selected row — a wash, not a fill. */
private const val SelectedFill = 0.10f

@OdoThemePreviews
@Composable
private fun OdoCarModelFieldPreview() = OdoPreview {
    var model by remember { mutableStateOf<OdoCarModel?>(previewModels.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoCarModelField(
            selected = model,
            models = previewModels,
            onSelect = { model = it },
            title = "Choose model",
            subtitle = "14 Honda models",
            searchPlaceholder = "Search models",
            matchCountLabel = { "$it MATCHES" },
            allSectionLabel = "ALL MODELS",
            emptyResultsText = "No models match",
            closeContentDescription = "Close",
            label = "Model",
            placeholder = "Choose",
        )
        OdoCarModelField(
            selected = null,
            models = previewModels,
            onSelect = {},
            title = "Choose model",
            subtitle = "14 Honda models",
            searchPlaceholder = "Search models",
            matchCountLabel = { "$it MATCHES" },
            allSectionLabel = "ALL MODELS",
            emptyResultsText = "No models match",
            closeContentDescription = "Close",
            placeholder = "Choose",
            errorText = "Model select karein",
        )
    }
}

/** Previews the sheet body on its own — the real sheet can't render in a preview. */
@OdoThemePreviews
@Composable
private fun OdoCarModelSheetPreview() = OdoPreview(padded = false) {
    CarModelSheet(
        selected = previewModels.first(),
        models = previewModels,
        title = "Choose model",
        subtitle = "14 Honda models",
        searchPlaceholder = "Search models",
        matchCountLabel = { "$it MATCHES" },
        allSectionLabel = "ALL MODELS",
        emptyResultsText = "No models match",
        closeContentDescription = "Close",
        onSelect = {},
        onClose = {},
    )
}

private val previewModels = listOf(
    OdoCarModel("city-vx-cvt", "City", "VX CVT"),
    OdoCarModel("city-v-mt", "City", "V MT"),
    OdoCarModel("amaze-s", "Amaze", "S MT"),
    OdoCarModel("elevate-zx", "Elevate", "ZX CVT"),
    OdoCarModel("jazz-vx", "Jazz", "VX MT"),
)
