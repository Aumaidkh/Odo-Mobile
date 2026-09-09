package com.hopcape.odo.web.blog.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.hopcape.odo.web.core.platform.decodeImageBytes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

/**
 * An image from a URL, or null while it is not there.
 *
 * Small on purpose. A cache, retries and placeholders are what an image library
 * is for, and this module has exactly two places that show a remote picture — a
 * screenshot in an action card and a tile in the media list. Pulling in a loader
 * for that would be more dependency than problem.
 *
 * Null covers three cases the caller wants to treat the same way: no URL yet,
 * still fetching, and could not be read. Each of them means "draw the slot".
 */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    val client: HttpClient = koinInject()
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        bitmap = try {
            decodeImageBytes(client.get(url).readRawBytes())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }
    return bitmap
}
