package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabasePublicAssetUrlsTest {

    @Test
    fun aPath_becomesAPublicObjectUrlOnTheConfiguredHost() {
        val urls = SupabasePublicAssetUrls(SupabaseEnvironment(url = "https://api.odoapp.in/", anonKey = "key"))

        assertEquals(
            "https://api.odoapp.in/storage/v1/object/public/vehicle-logos/makes/make-kia.png",
            urls.urlFor("vehicle-logos/makes/make-kia.png"),
        )
    }

    @Test
    fun anUnconfiguredBuild_hasNoUrls() {
        val urls = SupabasePublicAssetUrls(SupabaseEnvironment(url = "", anonKey = ""))

        assertNull(urls.urlFor("vehicle-logos/makes/make-kia.png"))
    }
}
