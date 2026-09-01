package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
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
 * One car **make** (brand) row in the [OdoCarMakeField] picker: a stable [id], the
 * brand [name], an optional [subtitle] line (e.g. "14 models"), and an optional
 * [monogram] override for the avatar tile.
 *
 * Copy-free like the rest of the design system: the component invents no counts. The
 * [subtitle] ("14 models") is formatted and localised by the caller — pass `null` to
 * omit it. [monogram] defaults to the first letter of [name] (so "Maruti Suzuki" → "M");
 * override it only when the brand's badge letter differs from its name.
 */
@Immutable
data class OdoCarMake(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val monogram: String? = null,
) {
    /** The avatar letter — [monogram] if given, else [name]'s first character, uppercased. */
    val avatarText: String
        get() = (monogram ?: name.trim().take(1)).uppercase()
}

/**
 * The **car make** picker — a collapsed field showing the chosen brand, which opens a
 * searchable bottom sheet of makes. Sibling of [OdoFuelTypeField]; use it anywhere a car's
 * brand is chosen (onboarding's "Choose make", editing a car). A later model picker reuses
 * this same shape.
 *
 * Typing filters [makes] by name (case-insensitive substring) into a "matches" section with
 * the matched span emphasised; a caller-curated [popular] shortcut list sits below and stays
 * visible while searching. With no query the sheet lists [popular] then every make. Tapping a
 * row commits immediately and closes the sheet — no confirm button.
 *
 * Copy-free: every string is a parameter. Section labels are rendered in the tracked-caps
 * caption role, so pass them already uppercased ("POPULAR IN PUNE"); [matchCountLabel] owns
 * its own pluralisation ("1 MATCH" / "3 MATCHES") so it stays in the feature's language.
 *
 * ```
 * OdoCarMakeField(
 *     selected = form.make,
 *     makes = state.makes,               // each: name + "14 models" subtitle
 *     popular = state.popularMakes,
 *     onSelect = { onEvent(MakeChanged(it)) },
 *     title = stringResource(Res.string.onb_make_sheet_title),
 *     subtitle = stringResource(Res.string.onb_make_sheet_subtitle, state.makes.size),
 *     searchPlaceholder = stringResource(Res.string.onb_make_search_hint),
 *     matchCountLabel = { pluralStringResource(Res.plurals.onb_make_matches, it, it) },
 *     popularSectionLabel = stringResource(Res.string.onb_make_popular),
 *     allSectionLabel = stringResource(Res.string.onb_make_all),
 *     emptyResultsText = stringResource(Res.string.onb_make_no_matches),
 *     closeContentDescription = stringResource(Res.string.onb_make_close),
 *     label = stringResource(Res.string.onb_label_make),
 *     placeholder = stringResource(Res.string.onb_make_placeholder),
 *     errorText = form.make.error?.asString(),
 * )
 * ```
 *
 * @param title / [subtitle] the sheet header (e.g. "Choose make" / "32 brands available").
 * @param searchPlaceholder hint shown in the empty search field.
 * @param matchCountLabel builds the matches-section header from the result count.
 * @param popularSectionLabel header above the [popular] shortcut list (e.g. "POPULAR IN PUNE").
 * @param allSectionLabel header above the full make list, shown only when not searching.
 * @param emptyResultsText shown in place of the matches list when a query matches nothing.
 * @param closeContentDescription screen-reader label for the sheet's close button.
 * @param label / [placeholder] the collapsed field's label and empty-state text.
 * @param errorText when non-null, the field turns danger-coloured and this shows below it,
 *   replacing [helperText] (same rule as [OdoInputField]).
 * @param notListedLabel when non-null, shows a footer row below the lists ("My car's brand
 *   isn't listed") that switches the sheet to a plain text field; confirming it calls
 *   [onSelect] with a synthetic [OdoCarMake] built from what was typed. `null` (the default)
 *   omits the row entirely, so existing call sites are unaffected. [notListedPlaceholder] and
 *   [notListedConfirmLabel] are required alongside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoCarMakeField(
    selected: OdoCarMake?,
    makes: List<OdoCarMake>,
    onSelect: (OdoCarMake) -> Unit,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    matchCountLabel: (Int) -> String,
    popularSectionLabel: String,
    allSectionLabel: String,
    emptyResultsText: String,
    closeContentDescription: String,
    modifier: Modifier = Modifier,
    popular: List<OdoCarMake> = emptyList(),
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    notListedLabel: String? = null,
    notListedPlaceholder: String? = null,
    notListedConfirmLabel: String? = null,
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
        CollapsedMakeField(
            make = selected,
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
            CarMakeSheet(
                selected = selected,
                makes = makes,
                popular = popular,
                title = title,
                subtitle = subtitle,
                searchPlaceholder = searchPlaceholder,
                matchCountLabel = matchCountLabel,
                popularSectionLabel = popularSectionLabel,
                allSectionLabel = allSectionLabel,
                emptyResultsText = emptyResultsText,
                closeContentDescription = closeContentDescription,
                notListedLabel = notListedLabel,
                notListedPlaceholder = notListedPlaceholder,
                notListedConfirmLabel = notListedConfirmLabel,
                onSelect = { make ->
                    onSelect(make)
                    dismiss()
                },
                onClose = { dismiss() },
            )
        }
    }
}

/* ------------------------------ Collapsed field ------------------------------ */

@Composable
private fun CollapsedMakeField(
    make: OdoCarMake?,
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
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (make != null) {
            MakeAvatar(make = make, selected = false, size = CollapsedAvatarSize)
        }
        OdoText(
            text = make?.name ?: placeholder.orEmpty(),
            style = OdoTheme.typography.heading,
            color = when {
                make == null -> colors.textMuted
                enabled -> colors.text
                else -> colors.textMuted
            },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        DownChevron()
    }
}

/** Down-chevron drawn in-house (no Material-icons dep), matching [OdoFuelTypeField]. */
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
private fun CarMakeSheet(
    selected: OdoCarMake?,
    makes: List<OdoCarMake>,
    popular: List<OdoCarMake>,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    matchCountLabel: (Int) -> String,
    popularSectionLabel: String,
    allSectionLabel: String,
    emptyResultsText: String,
    closeContentDescription: String,
    onSelect: (OdoCarMake) -> Unit,
    onClose: () -> Unit,
    notListedLabel: String? = null,
    notListedPlaceholder: String? = null,
    notListedConfirmLabel: String? = null,
) {
    val colors = OdoTheme.colors
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty()
    var enteringCustom by remember { mutableStateOf(false) }

    if (enteringCustom && notListedPlaceholder != null && notListedConfirmLabel != null) {
        NotListedEntry(
            title = title,
            placeholder = notListedPlaceholder,
            confirmLabel = notListedConfirmLabel,
            closeContentDescription = closeContentDescription,
            onConfirm = { typed -> onSelect(OdoCarMake(id = CUSTOM_MAKE_ID_PREFIX + typed, name = typed)) },
            onClose = onClose,
        )
        return
    }

    val matches = if (searching) {
        makes.filter { it.name.contains(trimmed, ignoreCase = true) }
    } else {
        emptyList()
    }
    // Popular is always a shortcut; while searching, drop rows already surfaced as matches.
    val popularRows = if (searching) popular.filter { p -> matches.none { it.id == p.id } } else popular
    // The full list shows only at rest, and never repeats the popular block above it.
    val allRows = if (searching) emptyList() else makes.filter { m -> popular.none { it.id == m.id } }

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
            if (searching) {
                item(key = "matches-header") {
                    SectionHeader(matchCountLabel(matches.size))
                }
                if (matches.isEmpty()) {
                    item(key = "matches-empty") {
                        OdoText(
                            emptyResultsText,
                            style = OdoTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = OdoTheme.spacing.xs),
                        )
                    }
                } else {
                    items(matches, key = { "match-${it.id}" }) { make ->
                        MakeRow(
                            make = make,
                            selected = make.id == selected?.id,
                            query = trimmed,
                            onClick = { onSelect(make) },
                        )
                    }
                }
            }

            if (popularRows.isNotEmpty()) {
                item(key = "popular-header") { SectionHeader(popularSectionLabel) }
                items(popularRows, key = { "popular-${it.id}" }) { make ->
                    MakeRow(
                        make = make,
                        selected = make.id == selected?.id,
                        query = "",
                        onClick = { onSelect(make) },
                    )
                }
            }

            if (allRows.isNotEmpty()) {
                item(key = "all-header") { SectionHeader(allSectionLabel) }
                items(allRows, key = { "all-${it.id}" }) { make ->
                    MakeRow(
                        make = make,
                        selected = make.id == selected?.id,
                        query = "",
                        onClick = { onSelect(make) },
                    )
                }
            }

            if (notListedLabel != null) {
                item(key = "not-listed") {
                    NotListedRow(text = notListedLabel, onClick = { enteringCustom = true })
                }
            }
        }
    }
}

/** The footer row offering the free-text escape hatch — a tap switches the sheet to [NotListedEntry]. */
@Composable
private fun NotListedRow(text: String, onClick: () -> Unit) {
    OdoText(
        text = text,
        style = OdoTheme.typography.bodySmall,
        color = OdoTheme.colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = OdoTheme.spacing.md),
    )
}

/**
 * The sheet body once "not listed" is tapped: one text field and a confirm button, in place
 * of every list — there is nothing left to search once the owner is naming their own brand.
 */
@Composable
private fun NotListedEntry(
    title: String,
    placeholder: String,
    confirmLabel: String,
    closeContentDescription: String,
    onConfirm: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = OdoTheme.colors
    var typed by remember { mutableStateOf("") }
    val trimmed = typed.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            OdoText(title, style = OdoTheme.typography.title, color = colors.text, modifier = Modifier.weight(1f))
            OdoIconButton(
                imageVector = IcClose,
                contentDescription = closeContentDescription,
                onClick = onClose,
                tint = colors.textDim,
                size = OdoTheme.iconSizes.medium,
            )
        }
        OdoInputField(value = typed, onValueChange = { typed = it }, placeholder = placeholder, singleLine = true)
        OdoButton(
            text = confirmLabel,
            onClick = { onConfirm(trimmed) },
            enabled = trimmed.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Prefix on a free-typed make's synthetic id — never matches a seeded make's slug-based id. */
private const val CUSTOM_MAKE_ID_PREFIX = "custom-"

/** A tracked-caps section eyebrow ("1 MATCH", "POPULAR IN PUNE"). */
@Composable
private fun SectionHeader(text: String) {
    OdoText(
        text = text,
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        modifier = Modifier.padding(top = OdoTheme.spacing.xs),
    )
}

/** One make row: avatar tile, name (with the matched span emphasised) + subtitle, and a check. */
@Composable
private fun MakeRow(
    make: OdoCarMake,
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
        MakeAvatar(make = make, selected = selected, size = RowAvatarSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(
                text = highlightedName(make.name, query, colors.text, colors.textDim),
                style = OdoTheme.typography.heading,
                color = colors.text,
                maxLines = 1,
            )
            if (make.subtitle != null) {
                OdoText(
                    make.subtitle,
                    style = OdoTheme.typography.bodySmall,
                    color = colors.textDim,
                    maxLines = 1,
                )
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

/** The rounded monogram tile at the head of a make row; fills with accent when chosen. */
@Composable
private fun MakeAvatar(make: OdoCarMake, selected: Boolean, size: Dp) {
    val colors = OdoTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(OdoTheme.shapes.small)
            .background(if (selected) colors.accent.copy(alpha = SelectedGlyphFill) else colors.surface)
            .border(1.dp, if (selected) colors.accent.copy(alpha = SelectedFill) else colors.border, OdoTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        OdoText(
            text = make.avatarText,
            style = OdoTheme.typography.heading,
            color = if (selected) colors.accent else colors.textDim,
            maxLines = 1,
        )
    }
}

/**
 * Emphasises the matched span of [name] against [query] — the matched characters render in
 * [matched] ink, the rest in [rest] — so the sheet echoes what was typed ("**Hon**da"). With
 * a blank [query] the whole name renders in [matched].
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

private val RowAvatarSize = 44.dp
private val CollapsedAvatarSize = 32.dp
private val SheetListMaxHeight = 420.dp

/** Accent tints for the selected row and its avatar tile — a wash, not a fill. */
private const val SelectedFill = 0.10f
private const val SelectedGlyphFill = 0.18f

@OdoThemePreviews
@Composable
private fun OdoCarMakeFieldPreview() = OdoPreview {
    var make by remember { mutableStateOf<OdoCarMake?>(previewMakes.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoCarMakeField(
            selected = make,
            makes = previewMakes,
            popular = previewPopular,
            onSelect = { make = it },
            title = "Choose make",
            subtitle = "32 brands available",
            searchPlaceholder = "Search brands",
            matchCountLabel = { "$it MATCH" },
            popularSectionLabel = "POPULAR IN PUNE",
            allSectionLabel = "ALL BRANDS",
            emptyResultsText = "No brands match",
            closeContentDescription = "Close",
            label = "Make",
            placeholder = "Choose",
        )
        OdoCarMakeField(
            selected = null,
            makes = previewMakes,
            popular = previewPopular,
            onSelect = {},
            title = "Choose make",
            subtitle = "32 brands available",
            searchPlaceholder = "Search brands",
            matchCountLabel = { "$it MATCH" },
            popularSectionLabel = "POPULAR IN PUNE",
            allSectionLabel = "ALL BRANDS",
            emptyResultsText = "No brands match",
            closeContentDescription = "Close",
            placeholder = "Choose",
            errorText = "Make select karein",
        )
    }
}

/** Previews the sheet body on its own — the real sheet can't render in a preview. */
@OdoThemePreviews
@Composable
private fun OdoCarMakeSheetPreview() = OdoPreview(padded = false) {
    CarMakeSheet(
        selected = previewMakes.first { it.name == "Honda" },
        makes = previewMakes,
        popular = previewPopular,
        title = "Choose make",
        subtitle = "32 brands available",
        searchPlaceholder = "Search brands",
        matchCountLabel = { "$it MATCH" },
        popularSectionLabel = "POPULAR IN PUNE",
        allSectionLabel = "ALL BRANDS",
        emptyResultsText = "No brands match",
        closeContentDescription = "Close",
        onSelect = {},
        onClose = {},
    )
}

private val previewMakes = listOf(
    OdoCarMake("honda", "Honda", "14 models"),
    OdoCarMake("maruti", "Maruti Suzuki", "22 models"),
    OdoCarMake("hyundai", "Hyundai", "16 models"),
    OdoCarMake("tata", "Tata", "11 models"),
    OdoCarMake("mahindra", "Mahindra", "13 models"),
    OdoCarMake("toyota", "Toyota", "9 models"),
    OdoCarMake("kia", "Kia", "6 models"),
)

private val previewPopular = listOf(
    OdoCarMake("maruti", "Maruti Suzuki", "22 models"),
    OdoCarMake("hyundai", "Hyundai", "16 models"),
    OdoCarMake("tata", "Tata", "11 models"),
)
