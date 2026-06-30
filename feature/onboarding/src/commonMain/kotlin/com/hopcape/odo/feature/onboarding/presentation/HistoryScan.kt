package com.hopcape.odo.feature.onboarding.presentation

/**
 * Port for the optional "scan your last service bill to auto-fill" step. The real
 * AI Bill Scanner is M2; M1 ships a stub so the flow compiles and skip always
 * works. Behind a port so M2 plugs in without touching the ViewModel.
 */
fun interface HistoryScanLauncher {
    suspend fun scan(): HistoryScanOutcome
}

/** Result of a scan attempt. M2 will add a `Prefilled(...)` case carrying extracted fields. */
sealed interface HistoryScanOutcome {
    /** Scanning isn't available yet (M1) — the user should Skip for now. */
    data object NotAvailable : HistoryScanOutcome
}

/** M1 stub: scanning is not wired up; always [HistoryScanOutcome.NotAvailable]. */
class StubHistoryScanLauncher : HistoryScanLauncher {
    override suspend fun scan(): HistoryScanOutcome = HistoryScanOutcome.NotAvailable
}
