package com.hopcape.odo.feature.support.presentation.diagnostics

import androidx.compose.runtime.Immutable

/** One line of what a diagnostics upload would contain. */
internal enum class DiagnosticPart {
    APP_AND_DEVICE,
    LOGS,
    BILL_SCANS,
    ACCOUNT_EMAIL,
}

/**
 * What would be sent, and how big it is.
 *
 * Four switches rather than a map of them: the set is fixed, the screen draws each one by
 * name, and a map of enum to boolean is a stability problem in exchange for nothing.
 *
 * The bill scans start **off** and everything else on. The other three describe the app; that
 * one is the owner's photographs of their own bills, and a screen that ships it ticked is a
 * screen that collects images by default.
 */
@Immutable
internal data class DiagnosticsUiState(
    val appAndDevice: Boolean = true,
    val logs: Boolean = true,
    val billScans: Boolean = false,
    val accountEmail: Boolean = true,
    /** "v1.3.3.3 · Pixel 7a · Android 14" — what the first line would actually carry. */
    val appLine: String = "",
    /** "last 7 days · 3 entries". */
    val logLine: String = "",
    /** The masked address, or blank when the account has none to send. */
    val emailLine: String = "",
    val sizeBytes: Long = 0L,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val sending: Boolean = false,
) {
    /** Nothing ticked is not a send. */
    val canSend: Boolean get() = appAndDevice || logs || billScans || accountEmail

    fun isOn(part: DiagnosticPart): Boolean = when (part) {
        DiagnosticPart.APP_AND_DEVICE -> appAndDevice
        DiagnosticPart.LOGS -> logs
        DiagnosticPart.BILL_SCANS -> billScans
        DiagnosticPart.ACCOUNT_EMAIL -> accountEmail
    }

    fun with(part: DiagnosticPart, on: Boolean): DiagnosticsUiState = when (part) {
        DiagnosticPart.APP_AND_DEVICE -> copy(appAndDevice = on)
        DiagnosticPart.LOGS -> copy(logs = on)
        DiagnosticPart.BILL_SCANS -> copy(billScans = on)
        DiagnosticPart.ACCOUNT_EMAIL -> copy(accountEmail = on)
    }

    internal companion object {
        /** What the screen promises, and what the server has to honour. */
        const val DEFAULT_RETENTION_DAYS = 30
    }
}

internal sealed interface DiagnosticsEvent {

    data object BackClicked : DiagnosticsEvent

    data class PartToggled(val part: DiagnosticPart, val on: Boolean) : DiagnosticsEvent

    data object SendClicked : DiagnosticsEvent
}
