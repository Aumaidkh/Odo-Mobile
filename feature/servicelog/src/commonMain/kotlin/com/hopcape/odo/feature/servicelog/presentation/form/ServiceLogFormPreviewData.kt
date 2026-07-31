package com.hopcape.odo.feature.servicelog.presentation.form

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.presentation.state.FormField
import kotlinx.datetime.LocalDate

/** A filled sample form (mirrors the mockup) — stands in for the ViewModel state. */
internal fun sampleFormState(editing: ServiceLogId? = null): ServiceLogFormUiState = ServiceLogFormUiState(
    mode = editing?.let(ServiceLogFormMode::Edit) ?: ServiceLogFormMode.Add,
    workshop = FormField(value = "Sharma Motors"),
    date = FormField(value = LocalDate(2026, 6, 12)),
    odometer = FormField(value = "54,000"),
    amount = FormField(value = "3,200"),
    categories = setOf(ServiceCategory.OIL_CHANGE),
)

@OdoThemePreviews
@Composable
private fun ServiceLogFormPreview() = OdoPreview(padded = false) {
    ServiceLogFormScreen(state = sampleFormState(), onEvent = {})
}
