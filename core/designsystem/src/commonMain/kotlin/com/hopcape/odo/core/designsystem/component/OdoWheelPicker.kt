package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcChevronDown
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * An iOS-style **wheel picker** — a vertical, snapping list where the value in the
 * centre band is the selection. Better UX than a long dropdown menu for ordered
 * sets like years. Purely display + selection: it reports the settled index via
 * [onSelectedIndexChange]; the caller owns the value.
 *
 * @param visibleCount odd number of rows shown at once (the middle one is selected).
 * @param selectionColor fill of the centre selection band (defaults to a raised
 *   surface); pass an accent wash to make the band read as "live".
 * @param selectionBorder optional stroke around the centre band — `null` leaves it
 *   borderless (the default), an accent [BorderStroke] rings the current value.
 * @param textStyle base style for every row; the centred row is scaled up from it,
 *   so pass a larger role (e.g. `title`) when the wheel is the screen's focus.
 */
@Composable
fun <T> OdoWheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 44.dp,
    selectionColor: Color = OdoTheme.colors.surfaceRaised,
    selectionBorder: BorderStroke? = null,
    textStyle: TextStyle = OdoTheme.typography.heading,
    label: (T) -> String = { it.toString() },
) {
    if (items.isEmpty()) return
    val start = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val edge = visibleCount / 2

    // The item whose centre is nearest the viewport centre = the current selection.
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                start
            } else {
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - mid) }!!.index
            }
        }
    }

    // Commit only once the wheel settles, not on every frame of a fling.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) onSelectedIndexChange(centeredIndex) }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Centre selection band behind the rows.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(OdoTheme.shapes.small)
                .background(selectionColor)
                .then(
                    if (selectionBorder != null) {
                        Modifier.border(selectionBorder, OdoTheme.shapes.small)
                    } else {
                        Modifier
                    },
                ),
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * edge),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items) { index, item ->
                val distance = abs(index - centeredIndex)
                val isCentre = distance == 0
                // The centred value grows; rows fall away in scale as they leave it.
                val scale by animateFloatAsState(
                    targetValue = when (distance) {
                        0 -> 1.5f
                        1 -> 1f
                        else -> 0.82f
                    },
                    label = "wheelItemScale",
                )
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    OdoText(
                        text = label(item),
                        style = textStyle,
                        color = if (isCentre) OdoTheme.colors.text else OdoTheme.colors.textMuted,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                }
            }
        }
    }
}

/**
 * A select **field** that opens an [OdoWheelPicker] in a bottom sheet — the picker
 * counterpart to [OdoDropdownField], with an identical field surface (read-only
 * [OdoInputField] + chevron, same label/error styling) but a nicer wheel for long,
 * ordered option lists (years). The pick is confirmed with [confirmLabel]; dismissing
 * without confirming leaves [selected] unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OdoWheelPickerField(
    selected: T?,
    options: List<T>,
    onSelect: (T) -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    label: String? = null,
    placeholder: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    sheetTitle: String? = label,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Box(modifier) {
        OdoInputField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = label,
            placeholder = placeholder,
            errorText = errorText,
            enabled = enabled,
            readOnly = true,
            trailingIcon = { PickerChevron() },
        )
        if (enabled) {
            Box(Modifier.matchParentSize().clickable { open = true })
        }
    }

    if (open && options.isNotEmpty()) {
        val startIndex = options.indexOf(selected).coerceAtLeast(0)
        var pendingIndex by remember { mutableStateOf(startIndex) }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = OdoTheme.colors.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OdoTheme.spacing.screenEdge)
                    .padding(bottom = OdoTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
            ) {
                if (sheetTitle != null) {
                    OdoText(sheetTitle, style = OdoTheme.typography.heading, color = OdoTheme.colors.text)
                }
                OdoWheelPicker(
                    items = options,
                    selectedIndex = startIndex,
                    onSelectedIndexChange = { pendingIndex = it },
                    label = optionLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
                OdoButton(
                    text = confirmLabel,
                    onClick = {
                        onSelect(options[pendingIndex])
                        scope.launch { sheetState.hide() }.invokeOnCompletion { open = false }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Static down-chevron trailing affordance for the picker field. */
@Composable
private fun PickerChevron() {
    OdoIcon(
        imageVector = IcChevronDown,
        contentDescription = null,
        tint = OdoTheme.colors.textDim,
        size = OdoTheme.iconSizes.medium,
    )
}

@OdoThemePreviews
@Composable
private fun OdoWheelPickerPreview() = OdoPreview {
    var year by remember { mutableStateOf(2020) }
    val years = (2026 downTo 1980).toList()
    OdoWheelPicker(
        items = years,
        selectedIndex = years.indexOf(year),
        onSelectedIndexChange = { year = years[it] },
    )
}
