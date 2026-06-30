package com.hopcape.odo.core.domain.owner.model

import kotlin.jvm.JvmInline

/**
 * Typed identity for the car's owner (the profile/auth user). The Car aggregate
 * references the Owner aggregate by this id only — never by embedding the object.
 */
@JvmInline
value class OwnerId(val value: String)
