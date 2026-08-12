package com.hopcape.odo.core.domain.servicelog.model

/**
 * How a [ServiceLogEntry] came to exist. Mirrors the DB `log_source` enum
 * (DB_SCHEMA §9.2). Manual entries created in M1 are [MANUAL] with a null `billId`;
 * [SCANNED] is the M2 Bill Scanner hook.
 */
enum class LogSource { MANUAL, SCANNED }
