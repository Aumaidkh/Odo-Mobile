package com.hopcape.odo.core.domain.servicelog.model

import kotlin.jvm.JvmInline

/**
 * Typed identity for a scanned bill. Nullable on a [ServiceLogEntry]: manual
 * entries have no bill. There is no `bills` table yet — this is the groundwork hook
 * for the M2 Bill Scanner, which attaches a bill to an entry with no migration.
 */
@JvmInline
value class BillId(val value: String)
