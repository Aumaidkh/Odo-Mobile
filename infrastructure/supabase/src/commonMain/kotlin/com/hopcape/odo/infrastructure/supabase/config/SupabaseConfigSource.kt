package com.hopcape.odo.infrastructure.supabase.config

import com.hopcape.odo.core.common.runCatchingCancellable
import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigSnapshotStore
import com.hopcape.odo.core.config.ConfigSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backs the config system with the `app_config` table.
 *
 * **Why not Remote Config.** Its API authenticates with a Google service-account
 * private key, which cannot be in a browser — so the admin panel could only reach it
 * through an edge function holding that key, set up across two consoles. A table is
 * read by the same PostgREST client everything else in this app already uses, and
 * written by the panel under the same RLS as every other admin write.
 *
 * **The values are held in memory and mirrored to [ConfigSnapshotStore].** Reads on
 * this interface are synchronous — [ConfigResolver] calls them during composition —
 * so they cannot go to the network, and the Firebase SDK's own disk cache has no
 * equivalent here. Without the store, every cold start would resolve to compiled
 * defaults until the first fetch landed, and a kill switch that does not apply for
 * the first seconds of a launch is not a kill switch.
 *
 * **A failed refresh changes nothing.** Not the memory map, not the store, not the
 * generation. An unreachable backend is a backend with nothing new to say, and the
 * last known values are a better answer than compiled defaults.
 */
internal class SupabaseConfigSource(
    private val postgrest: PostgrestClient,
    private val store: ConfigSnapshotStore,
) : ConfigSource, ConfigRefresher {

    /**
     * Seeded from disk at construction, so the very first read on a cold start already
     * has last launch's values rather than waiting for [refresh].
     */
    private var values: Map<String, String> = runCatchingCancellable { store.read() }.getOrElse { emptyMap() }

    private val _generation = MutableStateFlow(0L)
    override val generation: StateFlow<Long> = _generation.asStateFlow()

    /**
     * Reads the table, and bumps the counter only when something actually changed.
     *
     * The comparison is what keeps the flows quiet: [ConfigResolver.observe] maps this
     * counter, so a bump on every poll would re-render every screen reading a key,
     * several times an hour, for no change at all.
     */
    override suspend fun refresh() {
        val rows = runCatchingCancellable {
            postgrest.select(
                table = TABLE,
                serializer = ConfigRow.serializer(),
                // Only active rows. Parking one in the panel is meant to hand the key
                // back to its compiled default, and a device that kept reading it
                // would make Park do nothing.
                filters = mapOf(IS_ACTIVE to "is.true"),
                columns = "key,value",
            )
        }.getOrNull() ?: return

        val fetched = rows.associate { it.key to it.value }
        if (fetched == values) return

        values = fetched
        runCatchingCancellable { store.write(fetched) }
        _generation.value += 1
    }

    /**
     * Blank is "no value set", which the contract requires be indistinguishable from
     * absent.
     *
     * Trimmed first: a value pasted into an admin field picks up whitespace remarkably
     * often, and one leading space is enough to stop a number parsing.
     */
    private fun raw(key: String): String? = values[key]?.trim()?.takeIf { it.isNotEmpty() }

    // Strict, like the Firebase source it replaces: `yes`, `on` and `1` are not
    // booleans, so a key set to one of those falls through to its compiled default
    // rather than being read as true.
    override fun boolean(key: String): Boolean? = raw(key)?.toBooleanStrictOrNull()

    override fun int(key: String): Int? = raw(key)?.toIntOrNull()

    override fun long(key: String): Long? = raw(key)?.toLongOrNull()

    override fun double(key: String): Double? = raw(key)?.toDoubleOrNull()

    override fun string(key: String): String? = raw(key)

    private companion object {
        const val TABLE = "app_config"
        const val IS_ACTIVE = "is_active"
    }
}

@Serializable
private data class ConfigRow(
    val key: String,
    @SerialName("value") val value: String,
)
