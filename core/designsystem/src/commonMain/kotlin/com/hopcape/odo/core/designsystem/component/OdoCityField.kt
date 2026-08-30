package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
 * One **city** row in the [OdoCityField] picker: a stable [id], the city [name], and an
 * optional [subtitle] line (e.g. the state). A free-typed entry gets a synthetic id prefixed
 * `"custom-"` ([CUSTOM_CITY_ID_PREFIX]).
 *
 * Copy-free like the rest of the design system: the component invents no counts. The
 * [subtitle] is formatted and localised by the caller — pass `null` to omit it.
 */
@Immutable
data class OdoCity(
    val id: String,
    val name: String,
    val subtitle: String? = null,
)

/**
 * The **city** picker — a collapsed field showing the chosen city, which opens a searchable
 * bottom sheet of cities. Sibling of [OdoCarMakeField], but plain rows rather than a monogram
 * avatar — a city has no brand mark to show.
 *
 * Typing filters [cities] by name (case-insensitive substring) into a "matches" section with
 * the matched span emphasised. With no query the sheet lists every city. Tapping a row commits
 * immediately and closes the sheet — no confirm button.
 *
 * Copy-free: every string is a parameter. Section labels are rendered in the tracked-caps
 * caption role, so pass them already uppercased ("ALL CITIES"); [matchCountLabel] owns its own
 * pluralisation ("1 MATCH" / "3 MATCHES") so it stays in the feature's language.
 *
 * @param title / [subtitle] the sheet header (e.g. "Choose city" / "24 cities available").
 * @param searchPlaceholder hint shown in the empty search field.
 * @param matchCountLabel builds the matches-section header from the result count.
 * @param allSectionLabel header above the full city list, shown only when not searching.
 * @param emptyResultsText shown in place of the matches list when a query matches nothing.
 * @param closeContentDescription screen-reader label for the sheet's close button.
 * @param label / [placeholder] the collapsed field's label and empty-state text.
 * @param errorText when non-null, the field turns danger-coloured and this shows below it,
 *   replacing [helperText] (same rule as [OdoInputField]).
 * @param notListedLabel when non-null, reserves a footer row below the list ("My city isn't
 *   listed") that fades and expands into view only once a search turns up no match — there is
 *   nothing to escape to while the full list is showing or a query still matches something.
 *   Tapping it switches the sheet to a plain text field; confirming it calls [onSelect] with a
 *   synthetic [OdoCity] built from what was typed. `null` (the default) omits the row entirely,
 *   so existing call sites are unaffected. [notListedPlaceholder] and [notListedConfirmLabel]
 *   are required alongside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoCityField(
    selected: OdoCity?,
    cities: List<OdoCity>,
    onSelect: (OdoCity) -> Unit,
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
        CollapsedCityField(
            city = selected,
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
            CitySheet(
                selected = selected,
                cities = cities,
                title = title,
                subtitle = subtitle,
                searchPlaceholder = searchPlaceholder,
                matchCountLabel = matchCountLabel,
                allSectionLabel = allSectionLabel,
                emptyResultsText = emptyResultsText,
                closeContentDescription = closeContentDescription,
                notListedLabel = notListedLabel,
                notListedPlaceholder = notListedPlaceholder,
                notListedConfirmLabel = notListedConfirmLabel,
                onSelect = { city ->
                    onSelect(city)
                    dismiss()
                },
                onClose = { dismiss() },
            )
        }
    }
}

/* ------------------------------ Collapsed field ------------------------------ */

@Composable
private fun CollapsedCityField(
    city: OdoCity?,
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
        OdoText(
            text = city?.name ?: placeholder.orEmpty(),
            style = OdoTheme.typography.heading,
            color = when {
                city == null -> colors.textMuted
                enabled -> colors.text
                else -> colors.textMuted
            },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
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
private fun CitySheet(
    selected: OdoCity?,
    cities: List<OdoCity>,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    matchCountLabel: (Int) -> String,
    allSectionLabel: String,
    emptyResultsText: String,
    closeContentDescription: String,
    onSelect: (OdoCity) -> Unit,
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
            onConfirm = { typed -> onSelect(OdoCity(id = CUSTOM_CITY_ID_PREFIX + typed, name = typed)) },
            onClose = onClose,
        )
        return
    }

    val matches = if (searching) {
        cities.filter { it.name.contains(trimmed, ignoreCase = true) }
    } else {
        emptyList()
    }
    // The full list shows only at rest.
    val allRows = if (searching) emptyList() else cities

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
                    items(matches, key = { "match-${it.id}" }) { city ->
                        CityRow(
                            city = city,
                            selected = city.id == selected?.id,
                            query = trimmed,
                            onClick = { onSelect(city) },
                        )
                    }
                }
            }

            if (allRows.isNotEmpty()) {
                item(key = "all-header") { SectionHeader(allSectionLabel) }
                items(allRows, key = { "all-${it.id}" }) { city ->
                    CityRow(
                        city = city,
                        selected = city.id == selected?.id,
                        query = "",
                        onClick = { onSelect(city) },
                    )
                }
            }

            if (notListedLabel != null) {
                item(key = "not-listed") {
                    AnimatedVisibility(
                        visible = searching && matches.isEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        NotListedRow(text = notListedLabel, onClick = { enteringCustom = true })
                    }
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
 * of the list — there is nothing left to search once the owner is naming their own city.
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

/** Prefix on a free-typed city's synthetic id — never matches a seeded city's server id. */
private const val CUSTOM_CITY_ID_PREFIX = "custom-"

/** A tracked-caps section eyebrow ("1 MATCH", "ALL CITIES"). */
@Composable
private fun SectionHeader(text: String) {
    OdoText(
        text = text,
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        modifier = Modifier.padding(top = OdoTheme.spacing.xs),
    )
}

/** One city row: name (with the matched span emphasised) + subtitle, and a check. */
@Composable
private fun CityRow(
    city: OdoCity,
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
                text = highlightedName(city.name, query, colors.text, colors.textDim),
                style = OdoTheme.typography.heading,
                color = colors.text,
                maxLines = 1,
            )
            if (city.subtitle != null) {
                OdoText(
                    city.subtitle,
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

/**
 * Emphasises the matched span of [name] against [query] — the matched characters render in
 * [matched] ink, the rest in [rest] — so the sheet echoes what was typed ("**Pu**ne"). With a
 * blank [query] the whole name renders in [matched].
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
private fun OdoCityFieldPreview() = OdoPreview {
    var city by remember { mutableStateOf<OdoCity?>(previewCities.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoCityField(
            selected = city,
            cities = previewCities,
            onSelect = { city = it },
            title = "Choose city",
            subtitle = "24 cities available",
            searchPlaceholder = "Search cities",
            matchCountLabel = { "$it MATCH" },
            allSectionLabel = "ALL CITIES",
            emptyResultsText = "No cities match",
            closeContentDescription = "Close",
            label = "City",
            placeholder = "Choose",
        )
        OdoCityField(
            selected = null,
            cities = previewCities,
            onSelect = {},
            title = "Choose city",
            subtitle = "24 cities available",
            searchPlaceholder = "Search cities",
            matchCountLabel = { "$it MATCH" },
            allSectionLabel = "ALL CITIES",
            emptyResultsText = "No cities match",
            closeContentDescription = "Close",
            placeholder = "Choose",
            errorText = "City select karein",
        )
    }
}

/** Previews the sheet body on its own — the real sheet can't render in a preview. */
@OdoThemePreviews
@Composable
private fun OdoCitySheetPreview() = OdoPreview(padded = false) {
    CitySheet(
        selected = previewCities.first { it.name == "Pune" },
        cities = previewCities,
        title = "Choose city",
        subtitle = "24 cities available",
        searchPlaceholder = "Search cities",
        matchCountLabel = { "$it MATCH" },
        allSectionLabel = "ALL CITIES",
        emptyResultsText = "No cities match",
        closeContentDescription = "Close",
        onSelect = {},
        onClose = {},
        notListedLabel = "My city isn't listed",
        notListedPlaceholder = "Enter your city",
        notListedConfirmLabel = "Add",
    )
}

private val previewCities = listOf(
    OdoCity("pune", "Pune", "Maharashtra"),
    OdoCity("mumbai", "Mumbai", "Maharashtra"),
    OdoCity("delhi", "Delhi", "Delhi"),
    OdoCity("bengaluru", "Bengaluru", "Karnataka"),
    OdoCity("chennai", "Chennai", "Tamil Nadu"),
    OdoCity("hyderabad", "Hyderabad", "Telangana"),
)
