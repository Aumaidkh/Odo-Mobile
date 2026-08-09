package com.hopcape.odo.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.designsystem.component.OdoDocumentViewer
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.data.sync.BlobDownloader
import com.hopcape.odo.core.platform.file.StoredDocument
import com.hopcape.odo.core.platform.file.rememberStoredDocument
import com.hopcape.odo.shared.resources.Res
import com.hopcape.odo.shared.resources.fp_cd_close
import com.hopcape.odo.shared.resources.fp_missing_message
import com.hopcape.odo.shared.resources.fp_missing_title
import com.hopcape.odo.shared.resources.fp_page_failed
import com.hopcape.odo.shared.resources.fp_title_fallback
import com.hopcape.odo.shared.resources.fp_unsupported_message
import com.hopcape.odo.shared.resources.fp_unsupported_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The file preview's contribution to the navigation graph.
 *
 * It lives in `:shared` rather than in a feature, because the viewer is not one feature's: the
 * vault opens a policy with it, the service log a bill, and the scanner the photo it just took.
 * `:shared` is the module that already owns the navigation host and depends on both halves of
 * the viewer — the design system that draws it and `:core:platform` that reads the file — so
 * this is glue in the composition root rather than a module of its own.
 */
internal class FilePreviewEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.FilePreview> { key -> FilePreviewRoute(navigationManager, key) }
    }
}

internal val filePreviewModule = module {
    single { FilePreviewEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
    // A factory, so one visit to the viewer is one flow id — the same rule the feature
    // telemetry facades follow.
    factoryOf(::FilePreviewTelemetry)
}

@Composable
internal fun FilePreviewRoute(
    navigationManager: NavigationManager,
    key: OdoDestination.FilePreview,
) {
    val telemetry = rememberFilePreviewTelemetry()
    val close = { navigationManager.back() }

    // Two steps, because the file may not be here yet: work out a key this device can open —
    // fetching the bytes from the bucket if that is what it takes — and only then read it.
    when (val local = rememberLocalKey(key, telemetry)) {
        LocalKey.Resolving -> Box(Modifier.fillMaxSize(), Alignment.Center) { OdoLoadingIndicator() }

        LocalKey.Unavailable -> Unavailable(
            title = key.title,
            headline = Res.string.fp_missing_title,
            message = Res.string.fp_missing_message,
            onClose = close,
        )

        is LocalKey.Ready -> StoredFile(
            localKey = local.value,
            key = key,
            telemetry = telemetry,
            onClose = close,
        )
    }
}

/** The file, once there is a key that opens on this device. */
@Composable
private fun StoredFile(
    localKey: String,
    key: OdoDestination.FilePreview,
    telemetry: FilePreviewTelemetry,
    onClose: () -> Unit,
) {
    val close = onClose

    when (val document = rememberStoredDocument(localKey)) {
        StoredDocument.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { OdoLoadingIndicator() }

        StoredDocument.Missing -> {
            remember { telemetry.unavailable(FilePreviewTelemetry.Reason.MISSING) }
            Unavailable(
                title = key.title,
                headline = Res.string.fp_missing_title,
                message = Res.string.fp_missing_message,
                onClose = close,
            )
        }

        StoredDocument.Unsupported -> {
            remember { telemetry.unavailable(FilePreviewTelemetry.Reason.UNSUPPORTED) }
            Unavailable(
                title = key.title,
                headline = Res.string.fp_unsupported_title,
                message = Res.string.fp_unsupported_message,
                onClose = close,
            )
        }

        is StoredDocument.Ready -> OdoDocumentViewer(
            pageCount = document.pages.count,
            renderPage = { index, widthPx ->
                telemetry.page(index) { document.pages.render(index, widthPx) }
            },
            onClose = close,
            closeContentDescription = stringResource(Res.string.fp_cd_close),
            pageFailedLabel = stringResource(Res.string.fp_page_failed),
            title = key.title ?: stringResource(Res.string.fp_title_fallback),
        )
    }
}

/**
 * The file cannot be shown. Kept as an ordinary screen with a back button rather than the
 * viewer's own chrome: there is nothing to look at, so the only thing left to offer is the
 * way out and a sentence saying what happened.
 */
@Composable
private fun Unavailable(
    title: String?,
    headline: StringResource,
    message: StringResource,
    onClose: () -> Unit,
) {
    OdoScreen(
        title = title ?: stringResource(Res.string.fp_title_fallback),
        onBack = onClose,
        backContentDescription = stringResource(Res.string.fp_cd_close),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
            OdoEmptyState(
                title = stringResource(headline),
                message = stringResource(message),
                icon = {
                    OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.textMuted)
                },
            )
        }
    }
}

/** Whether there is a key on this device that opens the file the route was given. */
private sealed interface LocalKey {
    /** Still looking, and possibly fetching. */
    data object Resolving : LocalKey

    /** Not here and not gettable — no bucket to ask, or the fetch failed. */
    data object Unavailable : LocalKey

    data class Ready(val value: String) : LocalKey
}

/**
 * Turn what the row carries into a key this device can open.
 *
 * A row's path means one of two things: a key into app storage if the file was added on this
 * phone, or a path inside a bucket if the row arrived from the server. The second is what a
 * second phone sees — it syncs the row naming a bill but not the bytes — and it is why this
 * can take a network round trip and show a spinner. The fetched copy is kept, so this is the
 * only visit that waits.
 *
 * With no bucket to fall back on the key is used as-is, which is right for a file that was
 * never anywhere else: a scan the owner has just taken.
 */
@Composable
private fun rememberLocalKey(
    key: OdoDestination.FilePreview,
    telemetry: FilePreviewTelemetry,
): LocalKey {
    val koin = getKoin()
    val downloader = remember(koin) { koin.get<BlobDownloader>() }
    val bucket = remember(key.remoteBucket) {
        key.remoteBucket?.let { name -> RemoteBucket.entries.firstOrNull { it.name == name } }
    }

    val resolved by produceState<LocalKey>(LocalKey.Resolving, key, downloader) {
        value = if (bucket == null) {
            LocalKey.Ready(key.storageKey)
        } else {
            telemetry.restore { downloader.localCopyOf(bucket, key.storageKey) }
                ?.let(LocalKey::Ready)
                ?: LocalKey.Unavailable
        }
    }
    return resolved
}

/**
 * One telemetry instance for the whole visit.
 *
 * Resolved through `remember` rather than left to recomposition, so the flow id that ties this
 * visit's spans together is minted once — a fresh instance per frame would scatter them.
 */
@Composable
private fun rememberFilePreviewTelemetry(): FilePreviewTelemetry {
    val koin = getKoin()
    return remember(koin) { koin.get() }
}
