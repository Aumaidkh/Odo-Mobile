package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.domain.shared.PublicAssetUrls
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment

/** Public-bucket URLs on the project this build talks to, so prod goes through its own domain. */
internal class SupabasePublicAssetUrls(
    private val environment: SupabaseEnvironment,
) : PublicAssetUrls {

    override fun urlFor(path: String): String? =
        if (environment.isConfigured && path.isNotBlank()) "${environment.storageUrl}/object/public/${path.trimStart('/')}" else null
}
