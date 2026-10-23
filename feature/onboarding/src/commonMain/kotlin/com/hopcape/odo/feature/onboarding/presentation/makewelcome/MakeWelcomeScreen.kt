package com.hopcape.odo.feature.onboarding.presentation.makewelcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarMakeSheet
import com.hopcape.odo.core.designsystem.component.OdoCarMakeTile
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.onboarding.presentation.welcome.Entrance
import com.hopcape.odo.feature.onboarding.presentation.welcome.GlowHeight
import com.hopcape.odo.feature.onboarding.presentation.welcome.LegalFooter
import com.hopcape.odo.feature.onboarding.presentation.welcome.OdometerMark
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_all
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_close
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_empty
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_matches
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_not_listed
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_other
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_popular
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_search_hint
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_sheet_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_sheet_title
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_type_confirm
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_make_type_placeholder
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_sign_in
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_title
import org.jetbrains.compose.resources.stringResource

/**
 * The v2 Welcome (S1): the mark, the title, and a grid of make tiles where one tap starts
 * setup. The grid drops to 2 columns at a font scale of 1.3 or more so no name is cut off.
 */
@Composable
internal fun MakeWelcomeScreen(
    state: MakeWelcomeUiState,
    onEvent: (MakeWelcomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier.fillMaxSize().background(OdoTheme.colors.bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(GlowHeight)
                .background(
                    Brush.verticalGradient(listOf(OdoTheme.colors.accent.copy(alpha = 0.10f), Color.Transparent)),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OdoTheme.spacing.screenEdge),
        ) {
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            Entrance(0, visible) { OdometerMark() }
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            Entrance(1, visible) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    OdoText(
                        text = state.title ?: stringResource(Res.string.onb_welcome_title),
                        style = OdoTheme.typography.display.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        color = OdoTheme.colors.text,
                    )
                    OdoText(
                        text = stringResource(Res.string.onb_welcome_subtitle),
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            Entrance(2, visible) { MakeGrid(tiles = state.tiles, onEvent = onEvent) }
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            Entrance(3, visible) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OdoButton(
                        text = stringResource(Res.string.onb_welcome_sign_in),
                        onClick = { onEvent(MakeWelcomeEvent.SignInClicked) },
                        variant = OdoButtonVariant.Tertiary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LegalFooter(
                        onTerms = { onEvent(MakeWelcomeEvent.TermsClicked) },
                        onPrivacy = { onEvent(MakeWelcomeEvent.PrivacyClicked) },
                    )
                }
            }
            Spacer(Modifier.height(OdoTheme.spacing.lg))
        }
    }

    if (state.otherOpen) OtherMakeSheet(allMakes = state.allMakes, popular = state.tiles, onEvent = onEvent)
}

/** The tiles in catalog order, then "Other". */
@Composable
private fun MakeGrid(tiles: List<String>, onEvent: (MakeWelcomeEvent) -> Unit) {
    val columns = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 3
    val other = OdoCarMake(id = OTHER_ID, name = stringResource(Res.string.onb_welcome_make_other), monogram = "+")
    val cells = tiles.map { OdoCarMake(id = it, name = it) } + other
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                row.forEach { make ->
                    OdoCarMakeTile(
                        make = make,
                        onClick = {
                            onEvent(
                                if (make.id == OTHER_ID) MakeWelcomeEvent.OtherTapped
                                else MakeWelcomeEvent.MakeTapped(make.name),
                            )
                        },
                        modifier = Modifier.weight(1f).testTag(MakeWelcomeTestTags.tile(make.id)),
                    )
                }
                // Keeps a short last row's tiles the same width as the rows above.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OtherMakeSheet(allMakes: List<String>, popular: List<String>, onEvent: (MakeWelcomeEvent) -> Unit) {
    val makes = allMakes.map { OdoCarMake(id = it, name = it) }
    // A template rather than a resolved string: the count is only known inside the sheet.
    val matches = stringResource(Res.string.onb_welcome_make_matches)
    OdoCarMakeSheet(
        onDismissRequest = { onEvent(MakeWelcomeEvent.OtherDismissed) },
        makes = makes,
        popular = popular.map { OdoCarMake(id = it, name = it) },
        onSelect = { onEvent(MakeWelcomeEvent.OtherPicked(it.name)) },
        title = stringResource(Res.string.onb_welcome_make_sheet_title),
        subtitle = stringResource(Res.string.onb_welcome_make_sheet_subtitle, makes.size),
        searchPlaceholder = stringResource(Res.string.onb_welcome_make_search_hint),
        matchCountLabel = { count -> matches.replace("%1\$d", count.toString()) },
        popularSectionLabel = stringResource(Res.string.onb_welcome_make_popular),
        allSectionLabel = stringResource(Res.string.onb_welcome_make_all),
        emptyResultsText = stringResource(Res.string.onb_welcome_make_empty),
        closeContentDescription = stringResource(Res.string.onb_welcome_make_close),
        notListedLabel = stringResource(Res.string.onb_welcome_make_not_listed),
        notListedPlaceholder = stringResource(Res.string.onb_welcome_make_type_placeholder),
        notListedConfirmLabel = stringResource(Res.string.onb_welcome_make_type_confirm),
    )
}

/** Test tags for the instrumented suites. */
internal object MakeWelcomeTestTags {
    fun tile(id: String): String = "welcome_make_tile_$id"
    const val OTHER: String = "welcome_make_tile_$OTHER_ID"
}

private const val OTHER_ID = "other"
private const val LARGE_FONT_SCALE = 1.3f

private val previewState = MakeWelcomeUiState(
    tiles = listOf("Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda", "Renault"),
)

@OdoThemePreviews
@Composable
private fun MakeWelcomeScreenPreview() = OdoPreview(padded = false) {
    MakeWelcomeScreen(state = previewState, onEvent = {})
}

/** S1 at 1.3x font scale: the grid drops to 2 columns. */
@OdoThemePreviews
@Composable
private fun MakeWelcomeScreenLargeFontPreview() = OdoPreview(padded = false) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
        MakeWelcomeScreen(state = previewState, onEvent = {})
    }
}
