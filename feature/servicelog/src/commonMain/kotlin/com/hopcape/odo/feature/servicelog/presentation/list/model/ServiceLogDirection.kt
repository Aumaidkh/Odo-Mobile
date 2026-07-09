package com.hopcape.odo.feature.servicelog.presentation.list.model

/**
 * Which of the two mockup directions a service-log screen renders. Both are built so
 * they can be compared live; once one is chosen the loser (and this toggle) is deleted.
 *
 *  - [LEDGER] — 1a: cost & fairness first (spend/savings header, per-card verdict).
 *  - [TIMELINE] — 1b: resale-proof first (record score ring, verified timeline).
 */
internal enum class ServiceLogDirection { LEDGER, TIMELINE }