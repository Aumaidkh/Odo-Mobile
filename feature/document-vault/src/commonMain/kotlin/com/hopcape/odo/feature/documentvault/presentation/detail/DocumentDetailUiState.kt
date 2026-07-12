package com.hopcape.odo.feature.documentvault.presentation.detail

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/** A coverage line under "what's covered" — present (green check) or excluded (dash). */
internal enum class CoverKind { OWN_DAMAGE, THIRD_PARTY, ZERO_DEP, ENGINE }

internal data class CoverItem(val kind: CoverKind, val covered: Boolean)

/**
 * Display state for a single document's detail. Provider / policy / cover figures are
 * per-document data; money is [Amount] (paise). [validityProgress] (0..1) drives the
 * expiry bar. Modelled for Insurance (the richest doc); other types fill the same shape.
 */
internal data class DocumentDetailUiState(
    val docName: String,
    val provider: String,
    val subtitle: String,
    val verified: Boolean,
    val policyNumber: String,
    val sumInsured: Amount,
    val expiresInDays: Int,
    val validTill: LocalDate,
    val validityProgress: Float,
    val coverType: String,
    val premiumPerYear: Amount,
    val covers: List<CoverItem>,
)

/** Sample detail (mirrors the mockup) for previews + the pre-ViewModel route host. */
internal fun sampleDocumentDetail(): DocumentDetailUiState = DocumentDetailUiState(
    docName = "Insurance",
    provider = "SafeDrive General",
    subtitle = "MOTOR · COMPREHENSIVE",
    verified = true,
    policyNumber = "SD · 8842 · 2291",
    sumInsured = rupees(480_000),
    expiresInDays = 7,
    validTill = LocalDate(2026, 7, 3),
    validityProgress = 0.9f,
    coverType = "Comprehensive",
    premiumPerYear = rupees(11_400),
    covers = listOf(
        CoverItem(CoverKind.OWN_DAMAGE, covered = true),
        CoverItem(CoverKind.THIRD_PARTY, covered = true),
        CoverItem(CoverKind.ZERO_DEP, covered = true),
        CoverItem(CoverKind.ENGINE, covered = false),
    ),
)

private fun rupees(amount: Long): Amount = Amount.of(amount * 100).getOrElse { Amount.ZERO }
