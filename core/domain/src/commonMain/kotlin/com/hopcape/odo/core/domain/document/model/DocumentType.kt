package com.hopcape.odo.core.domain.document.model

/**
 * The kind of paper in a car's document vault.
 *
 * Shared kernel: the vault owns these, the garage shows which are on file, the timeline
 * logs their renewals, and the health score counts them — so the set lives here rather
 * than as a private enum per feature. Mirrors the `document_type` enum in the database
 * (DB_SCHEMA §9.7), which is authoritative for the cases.
 */
enum class DocumentType { INSURANCE, PUC, RC, LOAN, OTHER }
