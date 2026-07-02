package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// SentryDestination — secondary vendor, added only when a DSN is
// configured (CrashConfig.sentryDsn). Thin adapter over the Sentry
// SDK; real calls sketched in comments. Stub until the SDK is wired.
// ─────────────────────────────────────────────────────────────
internal class SentryDestination(
    private val dsn: String,
    private val emit: (String) -> Unit = { kotlin.io.println(it) },
) : CrashDestination {

    override val name: String = "sentry"

    override fun record(report: CrashReport) {
        // Sentry.captureException(...) with breadcrumbs + tags mapped from the report.
        emit("[Sentry] capturing ${report.crashId} to $dsn")
    }

    override fun setCustomKey(key: String, value: Any?) {
        // Sentry.configureScope { it.setTag(key, value.toString()) }
    }

    override fun setUserId(userId: String?) {
        // Sentry.configureScope { it.user = User().apply { id = userId } }
    }
}
