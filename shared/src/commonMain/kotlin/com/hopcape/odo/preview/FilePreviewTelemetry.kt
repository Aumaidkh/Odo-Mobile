package com.hopcape.odo.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for the file preview — technical only.
 *
 * There is no analytics here on purpose. *That a document was previewed* is a product outcome
 * the feature the owner tapped from already reports (it knows whether that was an insurance
 * policy or a service bill); counting it again here would put the same action on the dashboard
 * twice. What this owns is the part nobody else can see: how long a page took to draw, and
 * whether it drew at all.
 *
 * **No PII, and no storage keys.** A key contains the car id, so only the page index, the page
 * count and a reason ever leave this class.
 *
 * Every method hands its block's result back untouched, so instrumentation cannot change what
 * the viewer shows.
 */
internal class FilePreviewTelemetry(
    private val logger: Logger,
    private val tracer: PerformanceTracer,
    ids: IdGenerator,
) {

    private val flowTraceId: String = "${FLOW}_${ids.newId()}"
    private val flowTrace = PerfTrace(flowId = FLOW, traceId = flowTraceId)

    /**
     * The viewer opened on a file it cannot show. Worth a line either way: [Reason.MISSING]
     * means a row outlived its file, which is a restore-from-backup or a bug in the file
     * store, and [Reason.UNSUPPORTED] means the app accepted a file it cannot draw.
     */
    fun unavailable(reason: String) {
        logger.warn(TAG, Event.UNAVAILABLE, tc = flowTrace.toLog(), fields = mapOf(Key.REASON to reason))
    }

    /**
     * Times the trip to fetch a file this device does not have, and notes it when the file
     * does not arrive.
     *
     * The one span here that can be slow enough for the owner to feel it, and the one that
     * depends on the network — so it is what to look at when "my documents won't open on my
     * new phone" comes in.
     */
    suspend fun restore(fetch: suspend () -> String?): String? {
        val span = tracer.startSpan(Trace.RESTORE, flowTraceId)
        return try {
            fetch().also { restored ->
                if (restored == null) {
                    span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                    unavailable(Reason.NOT_RESTORED)
                }
            }
        } finally {
            tracer.endSpan(span)
        }
    }

    /**
     * Times one page's decode and notes it if the page came back blank.
     *
     * A failed page is not a crash and not an error the owner can act on — the viewer draws a
     * placeholder and the rest of the document still reads — so it is the log, not the crash
     * reporter, that has to carry it.
     */
    suspend fun page(index: Int, render: suspend () -> ImageBitmap?): ImageBitmap? {
        val span = tracer.startSpan(Trace.RENDER_PAGE, flowTraceId)
        span.setAttribute(Key.PAGE, index)
        return try {
            render().also { rendered ->
                if (rendered == null) {
                    span.setAttribute(Key.OUTCOME, Outcome.FAILED)
                    logger.error(
                        TAG,
                        Event.PAGE_FAILED,
                        tc = flowTrace.toLog(),
                        fields = mapOf(Key.PAGE to index),
                    )
                }
            }
        } finally {
            tracer.endSpan(span)
        }
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /** Log event names. Not analytics events — none of these are declared to the tracker. */
    object Event {
        const val UNAVAILABLE = "file_preview_unavailable"
        const val PAGE_FAILED = "file_preview_page_failed"
    }

    /** Span names. */
    object Trace {
        const val RENDER_PAGE = "file_preview_render_page"
        const val RESTORE = "file_preview_restore"
    }

    object Key {
        const val PAGE = "page"
        const val REASON = "reason"
        const val OUTCOME = "outcome"
    }

    /** Values for [Key.REASON]. */
    object Reason {
        const val MISSING = "missing"
        const val UNSUPPORTED = "unsupported"

        /** The file is not on this device and could not be fetched from its bucket. */
        const val NOT_RESTORED = "not_restored"
    }

    object Outcome {
        const val FAILED = "failed"
    }

    private companion object {
        const val TAG = "FILE_PREVIEW"
        const val FLOW = "file_preview"
    }
}
