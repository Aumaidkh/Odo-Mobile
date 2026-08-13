package com.hopcape.odo.core.navigation

/**
 * True when [destination] is a step of the add-a-document flow.
 *
 * The flow starts on the vault's add screen, hands off to the scanner for a photo or an
 * uploaded file, and ends on the vault's success screen — so its steps belong to two
 * features, and the set is named here where both can reach it.
 *
 * Pass it to [NavigationCommand.FinishFlow] whenever the flow ends: every step is popped,
 * so a finished add is never left under the screen the owner lands on, and back from the
 * vault does what back from the vault normally does.
 *
 * The capture screen counts only when it was opened for a document. The same screen also
 * photographs bills, and a bill capture belongs to the service log's flow, not this one.
 */
fun isAddDocumentFlowStep(destination: OdoDestination): Boolean = when (destination) {
    is OdoDestination.Documents.Add,
    is OdoDestination.Documents.AddSuccess,
    is OdoDestination.BillScanner.DocumentReview,
    -> true

    is OdoDestination.BillScanner.Capture -> destination.target == ScanTarget.Document
    else -> false
}
