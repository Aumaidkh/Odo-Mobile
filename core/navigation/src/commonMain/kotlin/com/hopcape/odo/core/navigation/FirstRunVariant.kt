package com.hopcape.odo.core.navigation

import kotlinx.serialization.Serializable

/**
 * Which first-run flow an owner is in. [V1] is the 1.4.4.2 flow, kept behind the
 * `first_run_v2_enabled` kill switch; [V2] starts with the make grid.
 */
@Serializable
enum class FirstRunVariant { V1, V2 }
