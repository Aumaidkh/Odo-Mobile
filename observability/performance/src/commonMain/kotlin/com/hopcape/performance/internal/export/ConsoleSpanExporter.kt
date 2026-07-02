package com.hopcape.performance.internal.export

import com.hopcape.performance.internal.model.CompletedSpan

// ─────────────────────────────────────────────────────────────
// ConsoleSpanExporter — debug-only exporter that prints each span
// as a one-line tree fragment, indented by whether it is a root or
// a child, e.g.
//
//   └─ span: checkout_flow (620ms) [trace=trace-checkout-001]
//      └─ span: inventory_check_api (580ms) error=OUT_OF_STOCK, http_status=409
//
// Added by the factory only when PerformanceConfig.isDebug is true.
// ─────────────────────────────────────────────────────────────
internal class ConsoleSpanExporter(
    private val println: (String) -> Unit = { kotlin.io.println(it) },
) : SpanExporter {

    override val name: String = "console"

    override fun export(span: CompletedSpan) {
        val indent = if (span.parentSpanId == null) "└─" else "   └─"
        val attrs = span.attributes.entries
            .joinToString(", ") { (k, v) -> "$k=$v" }
            .let { if (it.isEmpty()) "" else " $it" }
        println("[APM] $indent span: ${span.name} (${span.durationMs}ms) [trace=${span.traceId}]$attrs")
    }

    override fun flush() {}
}
