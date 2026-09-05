package com.hopcape.odo.core.domain.support

import kotlin.jvm.JvmInline

/** A ticket's identity. Generated on the device, so a row exists before the server sees it. */
@JvmInline
value class SupportTicketId(val value: String)
