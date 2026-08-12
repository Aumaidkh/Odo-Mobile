package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Full-screen reader for a stored file — a scanned bill, an insurance PDF, a photographed RC.
 *
 * The file is described as a page count plus a way to draw one page, so this knows nothing
 * about images, PDFs or where the bytes live; it draws whatever it is handed. A single-page
 * document simply never shows the pager controls.
 *
 * Pages are drawn only as they are reached, at the width they are about to occupy, so a long
 * PDF costs one page of bitmap rather than all of them.
 *
 * ```
 * OdoDocumentViewer(
 *     pageCount = pages.count,
 *     renderPage = pages::render,
 *     title = document.title,
 *     closeContentDescription = "Close",
 *     pageFailedLabel = "This page could not be shown",
 *     onClose = navigateBack,
 * )
 * ```
 *
 * @param renderPage draws page `index` at roughly `targetWidthPx`, or returns null if it
 *   cannot be drawn. One page failing does not stop the rest of the document being read.
 * @param pageFailedLabel shown in place of a page that returned null.
 * @param actions extra controls in the top bar, next to the close button — share, download.
 */
@Composable
fun OdoDocumentViewer(
    pageCount: Int,
    renderPage: suspend (index: Int, targetWidthPx: Int) -> ImageBitmap?,
    onClose: () -> Unit,
    closeContentDescription: String,
    pageFailedLabel: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    pageContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Box(modifier.fillMaxSize().background(OdoTheme.colors.bg)) {
        // A drag across a zoomed page moves the page instead of turning it — OdoZoomable
        // only takes the gesture once there is something to move, so the pager keeps the rest.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            DocumentPage(
                index = index,
                renderPage = renderPage,
                contentDescription = pageContentDescription,
                failedLabel = pageFailedLabel,
            )
        }

        ViewerBar(
            title = title,
            onClose = onClose,
            closeContentDescription = closeContentDescription,
            actions = actions,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (pageCount > 1) {
            OdoPageIndicator(
                pageCount = pageCount,
                selectedIndex = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OdoTheme.spacing.lg),
            )
        }
    }
}

/** One page: drawn at the width it is laid out at, and zoomable once it is there. */
@Composable
private fun DocumentPage(
    index: Int,
    renderPage: suspend (index: Int, targetWidthPx: Int) -> ImageBitmap?,
    contentDescription: String?,
    failedLabel: String,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val page by produceState<PageState>(PageState.Loading, index, widthPx) {
            value = renderPage(index, widthPx)?.let(PageState::Ready) ?: PageState.Failed
        }
        when (val state = page) {
            PageState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { OdoLoadingIndicator() }

            PageState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                OdoText(
                    failedLabel,
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(OdoTheme.spacing.xl),
                )
            }

            is PageState.Ready -> OdoZoomable(modifier = Modifier.fillMaxSize(), resetKey = index) {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private sealed interface PageState {
    data object Loading : PageState
    data object Failed : PageState
    data class Ready(val bitmap: ImageBitmap) : PageState
}

/**
 * The controls, floating over the page rather than pushing it down — the page is the point of
 * the screen, and a bar with its own background would eat into it on a small phone.
 */
@Composable
private fun ViewerBar(
    title: String?,
    onClose: () -> Unit,
    closeContentDescription: String,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg.copy(alpha = 0.85f))
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIconButton(IcClose, contentDescription = closeContentDescription, onClick = onClose)
        OdoText(
            title.orEmpty(),
            style = OdoTheme.typography.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@OdoThemePreviews
@Composable
private fun OdoDocumentViewerPreview() = OdoPreview(padded = false) {
    val page = remember { ImageBitmap(600, 900) }
    OdoDocumentViewer(
        pageCount = 3,
        renderPage = { _, _ -> page },
        onClose = {},
        closeContentDescription = "Close",
        pageFailedLabel = "This page could not be shown",
        title = "Insurance policy",
    )
}
