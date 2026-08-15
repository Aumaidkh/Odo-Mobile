package com.hopcape.odo.core.navigation

/**
 * True when [destination] is a step of the scan-a-bill errand.
 *
 * The errand runs from the viewfinder through the confirm step to the fairness report and
 * whatever the report leads to. It spans two features — the scanner takes and reads the
 * photo, the fairness check judges what it found — so the set is named here, where both can
 * reach it.
 *
 * Pass it to [NavigationCommand.LeaveFlow] when the errand is over. Every step comes off,
 * and the owner lands on whatever opened the scanner: the garage they started from, or the
 * service-log form that sent them for a photo. The report itself does not know which, and
 * does not have to.
 *
 * The capture screen counts only when it was opened for a bill. The same screen also
 * photographs documents, and those belong to the vault's add flow
 * ([isAddDocumentFlowStep]).
 */
fun isBillScanFlowStep(destination: OdoDestination): Boolean = when (destination) {
    is OdoDestination.Fairness,
    is OdoDestination.BillScanner.Review,
    is OdoDestination.BillScanner.SaveSuccess,
    is OdoDestination.BillScanner.ReportSuccess,
    is OdoDestination.BillScanner.ScanError,
    -> true

    is OdoDestination.BillScanner.Capture -> destination.target != ScanTarget.Document
    else -> false
}
