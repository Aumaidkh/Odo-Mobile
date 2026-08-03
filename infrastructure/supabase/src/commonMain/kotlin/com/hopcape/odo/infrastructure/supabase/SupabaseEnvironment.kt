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
) {

    /** Whether this build can reach a project. False on a fresh checkout. */
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()

    /** PostgREST base — every table and RPC hangs off this. */
    val restUrl: String get() = "$normalizedUrl/rest/v1"

    /** Storage base — bucket objects hang off this. */
    val storageUrl: String get() = "$normalizedUrl/storage/v1"

    /** A trailing slash in the configured URL would produce `//rest/v1`, which 404s. */
    private val normalizedUrl: String get() = url.trimEnd('/')

    companion object {
        /** The environment this build was compiled with. */
        fun fromBuild(): SupabaseEnvironment = SupabaseEnvironment(
            url = BuildSupabaseConfig.URL,
            anonKey = BuildSupabaseConfig.ANON_KEY,
        )
    }
}
