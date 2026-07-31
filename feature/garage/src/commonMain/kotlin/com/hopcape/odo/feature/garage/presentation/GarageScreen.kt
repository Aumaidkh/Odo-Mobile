package com.hopcape.odo.feature.garage.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.formatRegistrationNumber
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcCardFilled
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcDotsVertical
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcIdCard
import com.hopcape.odo.core.designsystem.icons.IcJournalPlus
import com.hopcape.odo.core.designsystem.icons.IcLeafFilled
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatKm
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.model.ServiceHistoryEntry
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_add
import com.hopcape.odo.feature.garage.resources.gr_add_car_subtitle
import com.hopcape.odo.feature.garage.resources.gr_add_car_title
import com.hopcape.odo.feature.garage.resources.gr_badge_self_reported
import com.hopcape.odo.feature.garage.resources.gr_badge_verified
import com.hopcape.odo.feature.garage.resources.gr_car_meta
import com.hopcape.odo.feature.garage.resources.gr_cat_ac
import com.hopcape.odo.feature.garage.resources.gr_cat_battery
import com.hopcape.odo.feature.garage.resources.gr_cat_brakes
import com.hopcape.odo.feature.garage.resources.gr_cat_electrical
import com.hopcape.odo.feature.garage.resources.gr_cat_general_service
import com.hopcape.odo.feature.garage.resources.gr_cat_oil_change
import com.hopcape.odo.feature.garage.resources.gr_cat_other
import com.hopcape.odo.feature.garage.resources.gr_cat_suspension
import com.hopcape.odo.feature.garage.resources.gr_cat_tyres
import com.hopcape.odo.feature.garage.resources.gr_cd_add_service
import com.hopcape.odo.feature.garage.resources.gr_cd_more
import com.hopcape.odo.feature.garage.resources.gr_doc_add
import com.hopcape.odo.feature.garage.resources.gr_doc_expired
import com.hopcape.odo.feature.garage.resources.gr_doc_expires_in
import com.hopcape.odo.feature.garage.resources.gr_doc_insurance
import com.hopcape.odo.feature.garage.resources.gr_doc_licence
import com.hopcape.odo.feature.garage.resources.gr_doc_loan
import com.hopcape.odo.feature.garage.resources.gr_doc_on_file
import com.hopcape.odo.feature.garage.resources.gr_doc_other
import com.hopcape.odo.feature.garage.resources.gr_doc_puc
import com.hopcape.odo.feature.garage.resources.gr_doc_rc
import com.hopcape.odo.feature.garage.resources.gr_doc_valid_till
import com.hopcape.odo.feature.garage.resources.gr_documents
import com.hopcape.odo.feature.garage.resources.gr_empty_add_docs
import com.hopcape.odo.feature.garage.resources.gr_empty_body
import com.hopcape.odo.feature.garage.resources.gr_empty_log_service
import com.hopcape.odo.feature.garage.resources.gr_empty_title
import com.hopcape.odo.feature.garage.resources.gr_error_title
import com.hopcape.odo.feature.garage.resources.gr_entries_summary
import com.hopcape.odo.feature.garage.resources.gr_entry_meta
import com.hopcape.odo.feature.garage.resources.gr_entry_untagged
import com.hopcape.odo.feature.garage.resources.gr_filter_all
import com.hopcape.odo.feature.garage.resources.gr_filter_battery
import com.hopcape.odo.feature.garage.resources.gr_filter_service
import com.hopcape.odo.feature.garage.resources.gr_filter_tyres
import com.hopcape.odo.feature.garage.resources.gr_manage
import com.hopcape.odo.feature.garage.resources.gr_no_entries
import com.hopcape.odo.feature.garage.resources.gr_no_plate
import com.hopcape.odo.feature.garage.resources.gr_odometer
import com.hopcape.odo.feature.garage.resources.gr_service_history
import com.hopcape.odo.feature.garage.resources.gr_title
import com.hopcape.odo.feature.garage.resources.gr_update
import org.jetbrains.compose.resources.stringResource

/**
 * The Garage tab — the car's "home base". Renders the empty "add your car" state until
 * there is a [Car], otherwise the populated view: the car card, the document overview, and
 * inline service history with category filters. State-free: it renders [state] and
 * forwards intents, formatting the domain's typed values (₹, km, dates) on the way out.
 */
@Composable
internal fun GarageScreen(
    state: GarageUiState,
    onEvent: (GarageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier, title = stringResource(Res.string.gr_title)) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { OdoLoadingIndicator() }

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoEmptyState(
                    title = stringResource(Res.string.gr_error_title),
                    message = content.message.asString(),
                    icon = { IconTile(IcCar, tint = OdoTheme.colors.danger, size = 88.dp) },
                )
            }

            is Loadable.Ready -> {
                val car = content.value.car
                if (car == null) {
                    EmptyGarage(contentPadding = padding, onEvent = onEvent)
                } else {
                    PopulatedGarage(
                        contentPadding = padding,
                        state = state,
                        content = content.value,
                        car = car,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

// --- Populated ------------------------------------------------------------------

@Composable
private fun PopulatedGarage(
    contentPadding: PaddingValues,
    state: GarageUiState,
    content: GarageContent,
    car: Car,
    onEvent: (GarageEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
    ) {
        CarCard(
            car = car,
            onUpdate = { onEvent(GarageEvent.UpdateOdometerTapped) },
            onMenu = { onEvent(GarageEvent.CarMenuTapped) },
        )
        DocumentsSection(
            documents = content.documents,
            onManage = { onEvent(GarageEvent.ManageDocumentsTapped) },
            onOpen = { onEvent(GarageEvent.DocumentTapped(it)) },
            onAdd = { onEvent(GarageEvent.AddDocumentTapped) },
        )
        ServiceHistorySection(
            entries = state.filteredHistory,
            total = state.filteredTotal.formatRupees(),
            filter = state.filter,
            onAdd = { onEvent(GarageEvent.AddServiceTapped) },
            onOpen = { onEvent(GarageEvent.ServiceTapped(it)) },
            onFilterChange = { onEvent(GarageEvent.FilterSelected(it)) },
        )
    }
}

@Composable
private fun CarCard(car: Car, onUpdate: () -> Unit, onMenu: () -> Unit) {
    OdoCard(modifier = Modifier.testTag(GarageTestTags.CAR_CARD)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarAvatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(car.displayName, style = OdoTheme.typography.heading, maxLines = 1)
                OdoText(
                    stringResource(Res.string.gr_car_meta, car.year.value, car.plateOrPlaceholder()),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                )
            }
            OdoIconButton(IcDotsVertical, contentDescription = stringResource(Res.string.gr_cd_more), onClick = onMenu)
        }
        OdoDivider(Modifier.padding(vertical = OdoTheme.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(stringResource(Res.string.gr_odometer), style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
                OdoText(
                    car.odometer.formatKm(),
                    style = OdoTheme.typography.title,
                    modifier = Modifier.testTag(GarageTestTags.ODOMETER),
                )
            }
            OdoButton(stringResource(Res.string.gr_update), onClick = onUpdate, variant = OdoButtonVariant.Secondary)
        }
    }
}

/** The plate, grouped for reading — or a dash, since a car may be registered but unrecorded. */
@Composable
private fun Car.plateOrPlaceholder(): String =
    registrationNumber?.let { formatRegistrationNumber(it.value) } ?: stringResource(Res.string.gr_no_plate)

@Composable
private fun DocumentsSection(
    documents: List<GarageDocument>,
    onManage: () -> Unit,
    onOpen: (DocumentId) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        SectionHeader(title = stringResource(Res.string.gr_documents)) { ManageLink(onManage) }
        OdoCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            documents.forEachIndexed { index, doc ->
                DocumentRow(
                    doc = doc,
                    onClick = { if (doc is GarageDocument.OnFile) onOpen(doc.document.id) else onAdd() },
                )
                if (index < documents.lastIndex) OdoDivider()
            }
        }
    }
}

@Composable
private fun DocumentRow(doc: GarageDocument, onClick: () -> Unit) {
    val missing = doc is GarageDocument.Missing
    val ink = if (missing) OdoTheme.colors.textMuted else OdoTheme.colors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GarageTestTags.documentRow(doc.type))
            .clickable(onClick = onClick)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(doc.type.icon(), contentDescription = null, tint = ink, size = OdoTheme.iconSizes.medium)
        OdoText(doc.type.label(), style = OdoTheme.typography.heading, color = ink, maxLines = 1, modifier = Modifier.weight(1f))
        when (doc) {
            is GarageDocument.Missing ->
                OdoText(stringResource(Res.string.gr_doc_add), style = OdoTheme.typography.label, color = OdoTheme.colors.accent)

            is GarageDocument.OnFile ->
                OdoBadge(doc.validity.label(), tone = doc.validity.tone())
        }
    }
}

@Composable
private fun ServiceHistorySection(
    entries: List<ServiceHistoryEntry>,
    total: String,
    filter: ServiceFacet,
    onAdd: () -> Unit,
    onOpen: (ServiceLogId) -> Unit,
    onFilterChange: (ServiceFacet) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        SectionHeader(title = stringResource(Res.string.gr_service_history)) {
            OdoIconButton(IcJournalPlus, contentDescription = stringResource(Res.string.gr_cd_add_service), onClick = onAdd)
        }
        OdoText(
            stringResource(Res.string.gr_entries_summary, entries.size, total),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            ServiceFacet.entries.forEach { facet ->
                OdoChip(label = facet.label(), selected = facet == filter, onClick = { onFilterChange(facet) })
            }
        }
        if (entries.isEmpty()) {
            OdoText(
                stringResource(Res.string.gr_no_entries),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
                modifier = Modifier.padding(vertical = OdoTheme.spacing.sm),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.cardGap)) {
                entries.forEach { entry -> ServiceEntryCard(entry = entry, onClick = { onOpen(entry.id) }) }
            }
        }
    }
}

@Composable
private fun ServiceEntryCard(entry: ServiceHistoryEntry, onClick: () -> Unit) {
    OdoCard(onClick = onClick, modifier = Modifier.testTag(GarageTestTags.serviceRow(entry.id.value))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(entry.title(), style = OdoTheme.typography.heading, maxLines = 2)
                OdoText(
                    entry.meta(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                )
            }
            TrustBadge(entry.isVerified)
            OdoText(entry.amount.formatRupees(), style = OdoTheme.typography.heading, maxLines = 1)
        }
    }
}

@Composable
private fun TrustBadge(verified: Boolean) = if (verified) {
    OdoBadge(
        stringResource(Res.string.gr_badge_verified),
        tone = OdoBadgeTone.Success,
        leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
    )
} else {
    OdoBadge(stringResource(Res.string.gr_badge_self_reported))
}

// --- Empty ----------------------------------------------------------------------

@Composable
private fun EmptyGarage(contentPadding: PaddingValues, onEvent: (GarageEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        AddYourCarCard(onAdd = { onEvent(GarageEvent.AddCarTapped) })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OdoEmptyState(
                title = stringResource(Res.string.gr_empty_title),
                message = stringResource(Res.string.gr_empty_body),
                icon = { CarAvatar(size = 88.dp) },
            )
        }
        EmptyActionCard(
            icon = IcShieldFilled,
            label = stringResource(Res.string.gr_empty_add_docs),
            onClick = { onEvent(GarageEvent.AddDocumentTapped) },
        )
        EmptyActionCard(
            icon = IcJournalPlus,
            label = stringResource(Res.string.gr_empty_log_service),
            onClick = { onEvent(GarageEvent.AddServiceTapped) },
        )
        Spacer(Modifier.height(OdoTheme.spacing.md))
    }
}

@Composable
private fun AddYourCarCard(onAdd: () -> Unit) {
    OdoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarAvatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(stringResource(Res.string.gr_add_car_title), style = OdoTheme.typography.heading, maxLines = 1)
                OdoText(stringResource(Res.string.gr_add_car_subtitle), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
            }
            OdoButton(stringResource(Res.string.gr_add), onClick = onAdd)
        }
    }
}

@Composable
private fun EmptyActionCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    OdoCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(icon, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
            OdoText(label, style = OdoTheme.typography.heading, maxLines = 1)
        }
    }
}

// --- Shared bits + mappings -----------------------------------------------------

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(title, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun ManageLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = OdoTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(stringResource(Res.string.gr_manage), style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
        OdoIcon(IcChevronRight, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
    }
}

private fun DocumentType.icon(): ImageVector = when (this) {
    DocumentType.INSURANCE -> IcShieldFilled
    DocumentType.PUC -> IcLeafFilled
    DocumentType.RC -> IcCardFilled
    DocumentType.LICENCE -> IcIdCard
    DocumentType.LOAN, DocumentType.OTHER -> IcFileFilled
}

@Composable
private fun DocumentType.label(): String = stringResource(
    when (this) {
        DocumentType.INSURANCE -> Res.string.gr_doc_insurance
        DocumentType.PUC -> Res.string.gr_doc_puc
        DocumentType.RC -> Res.string.gr_doc_rc
        DocumentType.LICENCE -> Res.string.gr_doc_licence
        DocumentType.LOAN -> Res.string.gr_doc_loan
        DocumentType.OTHER -> Res.string.gr_doc_other
    },
)

/** "Valid · 3 Jul 2027" / "Expires in 7 days" / "Expired 2 Jun 2026". */
@Composable
private fun DocumentValidity.label(): String = when (this) {
    DocumentValidity.NoExpiry -> stringResource(Res.string.gr_doc_on_file)
    is DocumentValidity.Valid -> stringResource(Res.string.gr_doc_valid_till, formatDate(until))
    is DocumentValidity.ExpiringSoon -> stringResource(Res.string.gr_doc_expires_in, daysLeft)
    is DocumentValidity.Expired -> stringResource(Res.string.gr_doc_expired, formatDate(since))
}

private fun DocumentValidity.tone(): OdoBadgeTone = when (this) {
    is DocumentValidity.Expired -> OdoBadgeTone.Danger
    is DocumentValidity.ExpiringSoon -> OdoBadgeTone.Warning
    is DocumentValidity.Valid, DocumentValidity.NoExpiry -> OdoBadgeTone.Success
}

/**
 * What the row is headed with: the entry's tags, read together. An entry nobody tagged
 * still gets a heading — the domain has no "work done" text to fall back on.
 */
@Composable
private fun ServiceHistoryEntry.title(): String = if (categories.isEmpty()) {
    stringResource(Res.string.gr_entry_untagged)
} else {
    categories.sortedBy { it.ordinal }.map { it.label() }.joinToString(" · ")
}

/** The date, plus where the work was done when that is on record. */
@Composable
private fun ServiceHistoryEntry.meta(): String {
    val date = formatDate(servicedOn)
    val workshop = workshopName ?: return date
    return stringResource(Res.string.gr_entry_meta, date, workshop.value)
}

@Composable
private fun ServiceCategory.label(): String = stringResource(
    when (this) {
        ServiceCategory.OIL_CHANGE -> Res.string.gr_cat_oil_change
        ServiceCategory.BRAKES -> Res.string.gr_cat_brakes
        ServiceCategory.TYRES -> Res.string.gr_cat_tyres
        ServiceCategory.AC -> Res.string.gr_cat_ac
        ServiceCategory.BATTERY -> Res.string.gr_cat_battery
        ServiceCategory.SUSPENSION -> Res.string.gr_cat_suspension
        ServiceCategory.ELECTRICAL -> Res.string.gr_cat_electrical
        ServiceCategory.GENERAL_SERVICE -> Res.string.gr_cat_general_service
        ServiceCategory.OTHER -> Res.string.gr_cat_other
    },
)

@Composable
private fun ServiceFacet.label(): String = stringResource(
    when (this) {
        ServiceFacet.ALL -> Res.string.gr_filter_all
        ServiceFacet.SERVICE -> Res.string.gr_filter_service
        ServiceFacet.TYRES -> Res.string.gr_filter_tyres
        ServiceFacet.BATTERY -> Res.string.gr_filter_battery
    },
)

// --- Previews -------------------------------------------------------------------

@OdoThemePreviews
@Composable
private fun GaragePopulatedPreview() = OdoPreview(padded = false) {
    GarageScreen(state = sampleGarage(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun GarageEmptyPreview() = OdoPreview(padded = false) {
    GarageScreen(state = sampleEmptyGarage(), onEvent = {})
}

/** The default state a ViewModel emits first — nothing read yet. */
@OdoThemePreviews
@Composable
private fun GarageLoadingPreview() = OdoPreview(padded = false) {
    GarageScreen(state = GarageUiState(), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun GarageFailedPreview() = OdoPreview(padded = false) {
    GarageScreen(state = sampleFailedGarage(), onEvent = {})
}
