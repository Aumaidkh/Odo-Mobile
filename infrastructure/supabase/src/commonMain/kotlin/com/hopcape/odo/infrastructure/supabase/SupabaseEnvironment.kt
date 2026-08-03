package com.hopcape.odo.infrastructure.supabase

/**
 * Which Supabase project this build talks to, and whether it has one at all.
 *
 * The values come from `local.properties` via the generated [BuildSupabaseConfig] (see the
 * `generateSupabaseConfig` task). A checkout without credentials produces blanks, and
 * [isConfigured] is what `supabaseModule` reads to decide between the real adapters and the
 * offline fakes. Odo works fully offline, so no credentials is a supported state, not an error.
 *
 * The anon key is not a secret in the way the service_role key is — it is meant to ship in
 * clients, and row-level security is what actually protects the data (DB_SCHEMA §12). It is
 * still kept out of git because it names a specific project.
 */
internal data class SupabaseEnvironment(
    val url: String,
    val anonKey: String,
    /**
     * Whether to sign in with real phone OTP rather than the development account.
     *
     * Config rather than a code branch, so the day DLT registration clears is a
     * `local.properties` edit and not a commit.
     */
    val usePhoneAuth: Boolean = false,
) {

    /** Whether this build can reach a project. False on a fresh checkout. */
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()

    /** PostgREST base — every table and RPC hangs off this. */
    val restUrl: String get() = "$normalizedUrl/rest/v1"

    /** Storage base — bucket objects hang off this. */
    val storageUrl: String get() = "$normalizedUrl/storage/v1"

    /** GoTrue base — sign-in and token refresh hang off this. */
    val authUrl: String get() = "$normalizedUrl/auth/v1"

    /** A trailing slash in the configured URL would produce `//rest/v1`, which 404s. */
    private val normalizedUrl: String get() = url.trimEnd('/')

    companion object {
        /** The environment this build was compiled with. */
        fun fromBuild(): SupabaseEnvironment = SupabaseEnvironment(
            url = BuildSupabaseConfig.URL,
            anonKey = BuildSupabaseConfig.ANON_KEY,
            usePhoneAuth = BuildSupabaseConfig.PHONE_AUTH,
        )
    }
}
