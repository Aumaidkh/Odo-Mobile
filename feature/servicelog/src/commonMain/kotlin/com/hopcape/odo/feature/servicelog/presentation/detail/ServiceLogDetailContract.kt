package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

internal sealed interface ServiceLogDetailEvent {
    data object EditClicked : ServiceLogDetailEvent
    data object DeleteClicked : ServiceLogDetailEvent
    data object ConfirmDelete : ServiceLogDetailEvent
    data object DismissDelete : ServiceLogDetailEvent
    data object ReportOverchargeClicked : ServiceLogDetailEvent
    data object Back : ServiceLogDetailEvent
}

internal sealed interface ServiceLogDetailEffect {
    data class OpenEdit(val id: ServiceLogId) : ServiceLogDetailEffect
    data object Back : ServiceLogDetailEffect
}
