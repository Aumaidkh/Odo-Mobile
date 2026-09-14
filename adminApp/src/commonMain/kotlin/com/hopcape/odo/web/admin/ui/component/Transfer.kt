package com.hopcape.odo.web.admin.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.hopcape.odo.web.admin.domain.CatalogueDocument
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_transfer_export
import com.hopcape.odo.web.admin.resources.ad_transfer_import
import com.hopcape.odo.web.admin.ui.DownloadFile
import com.hopcape.odo.web.admin.ui.browserFiles
import org.jetbrains.compose.resources.stringResource

/**
 * Export and import, for a section whose rows are reference data.
 *
 * The catalogs are built in dev — somebody adds forty trims, fixes a benchmark —
 * and have to end up in production. These two buttons are the whole of that: a file
 * out of one panel and into the other.
 *
 * Only reference data gets these. Nothing owner-scoped is ever exported from here.
 */
@Composable
fun TransferActions(
    onExport: () -> Unit,
    onImport: (document: String) -> Unit,
    enabled: Boolean = true,
) {
    val files = remember { browserFiles() }
    RowAction(stringResource(Res.string.ad_transfer_export), onExport, enabled)
    RowAction(
        label = stringResource(Res.string.ad_transfer_import),
        onClick = { files.pickText(CatalogueDocument.ACCEPT, onImport) },
        enabled = enabled,
    )
}

/**
 * Hands a prepared file to the browser, once, and says so.
 *
 * Keyed on the file itself: without [onHandled] clearing it, any later
 * recomposition with the same value would download it again.
 */
@Composable
fun HandleDownload(download: DownloadFile?, onHandled: () -> Unit) {
    val files = remember { browserFiles() }
    LaunchedEffect(download) {
        if (download != null) {
            files.download(download.fileName, download.mimeType, download.text)
            onHandled()
        }
    }
}
