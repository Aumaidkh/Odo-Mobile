package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A sheet the owner cannot get rid of, for the rare state where the app has to stop and there
 * is nothing to decide — a maintenance window, a version that may no longer run.
 *
 * Every other sheet here is dismissed by swipe, scrim or back. This one refuses all three, so
 * the screen behind it stays visible but unreachable. Use it only when carrying on is not an
 * option; a question with an answer belongs in [OdoConfirmSheet].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun OdoBlockingSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Out here, not in the content: the content is composed into the sheet's own window,
    // where a handler never sees the press that goes on to finish the activity.
    BackHandler(enabled = true) {}

    // Refusing Hidden is what stops the swipe: a ModalBottomSheet that reaches Hidden takes
    // itself out of the composition, whatever the caller wanted.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    ModalBottomSheet(
        // Scrim taps arrive here. Answering nothing is what makes them do nothing.
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = OdoTheme.colors.surface,
        // No handle: it is the affordance for a drag this sheet will not accept.
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OdoTheme.spacing.screenEdge)
                // Top padding is explicit here, unlike the other sheets: dropping the drag
                // handle also dropped the only thing holding content off the top edge.
                .padding(top = OdoTheme.spacing.xl, bottom = OdoTheme.spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
            content = content,
        )
    }
}
