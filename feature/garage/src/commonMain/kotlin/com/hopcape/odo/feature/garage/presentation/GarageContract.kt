package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet

/** What the owner did on the garage's home base, as data. */
internal sealed interface GarageEvent {

    /** The history chip that was tapped. The only event that changes what is on screen. */
    data class FilterSelected(val facet: ServiceFacet) : GarageEvent

    data object AddCarTapped : GarageEvent
    data object UpdateOdometerTapped : GarageEvent
    data object CarMenuTapped : GarageEvent
    data object ManageDocumentsTapped : GarageEvent
    data class DocumentTapped(val id: DocumentId) : GarageEvent
    data object AddDocumentTapped : GarageEvent
    data object AddServiceTapped : GarageEvent
    data class ServiceTapped(val id: ServiceLogId) : GarageEvent
}

/**
 * One-shot handoffs the route host performs. Each carries data; the route turns it into a
 * navigation command, which is what keeps the ViewModel free of navigation types.
 */
internal sealed interface GarageEffect {

    data object OpenAddCar : GarageEffect
    data object OpenUpdateOdometer : GarageEffect
    data object OpenCarActions : GarageEffect
    data object OpenDocumentVault : GarageEffect
    data class OpenDocument(val id: DocumentId) : GarageEffect
    data object OpenAddDocument : GarageEffect
    data object OpenAddToHistory : GarageEffect

    /** Opening one service needs the car it belongs to, which the key carries. */
    data class OpenService(val logId: ServiceLogId, val carId: String) : GarageEffect
}
