package com.hopcape.odo.core.domain.document.model

/**
 * The kind of paper in a car's document vault.
 *
 * Shared kernel: the vault owns these, the garage shows which are on file, the timeline
 * logs their renewals, and the health score counts them — so the set lives here rather
 * than as a private enum per feature. Mirrors the `document_type` enum in the database
 * (DB_SCHEMA §9.7), which is authoritative for the cases.
 *
 * [LICENCE] is car-scoped like the rest even though a driving licence belongs to the
 * owner rather than the car: `documents` hangs off `cars`, and the vault tracks it
 * alongside the papers a driver is stopped for. Multi-car (Phase 2) will therefore show
 * it once per car — accepted, rather than paying for a second owner-scoped table in the
 * MVP. The DB enum gains `licence` when the data slice lands.
 */
enum class DocumentType { INSURANCE, PUC, RC, LICENCE, LOAN, OTHER }
