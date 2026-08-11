package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.domain.legal.LegalLinks
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment

/**
 * The published legal pages as *this build* knows them — routes on the project it was
 * compiled against.
 *
 * The build-time half of [LegalLinks]: `RemoteConfigLegalLinks` sits in front and prefers
 * whatever the Firebase console says, falling back here. So this is the answer for a launch
 * before the first config fetch lands, and for a device that never reaches Firebase at all —
 * which on a privacy screen is the difference between working links and no policy.
 *
 * Derived from the configured project URL rather than hard-coded, so the address is never a
 * checked-in constant naming one specific project. Edge Function routes rather than a static
 * site because of the third one: the deletion page has to prove who is asking and then
 * actually erase something, and the key that can do the erasing has to stay on the server.
 *
 * On a checkout with no Supabase credentials every link is blank. That is a supported state
 * — Odo runs fully offline — and the callers check before offering a row, so an unconfigured
 * build shows no dead links rather than links that go nowhere.
 */
internal class SupabaseLegalLinks(
    private val environment: SupabaseEnvironment,
) : LegalLinks {

    override val privacyPolicy: String get() = route("privacy")

    override val termsOfUse: String get() = route("terms")

    override val deleteAccount: String get() = route("delete-account")

    private fun route(page: String): String =
        if (environment.isConfigured) "${environment.functionsUrl}/$FUNCTION/$page" else ""

    private companion object {
        /** The slug `supabase/functions/legal` is deployed under. */
        const val FUNCTION = "legal"
    }
}
