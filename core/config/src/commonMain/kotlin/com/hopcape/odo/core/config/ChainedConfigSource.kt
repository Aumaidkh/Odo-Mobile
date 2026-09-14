package com.hopcape.odo.core.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Several backends read as one, in order: the first that has a value for the key answers.
 *
 * Precedence is decided per key, not per backend. A key Remote Config holds comes from
 * Remote Config; the key beside it, which only the `app_config` table holds, still comes
 * from the table. Neither backend knows the other exists.
 *
 * A source that also implements [ConfigRefresher] is refreshed by [refresh]; one that does
 * not is read-only and skipped. An empty chain answers `null` to everything, which is the
 * correct behaviour for a build with no backend wired.
 */
class ChainedConfigSource(
    private val sources: List<ConfigSource>,
    private val onRefreshFailure: (Throwable) -> Unit = {},
) : ConfigSource, ConfigRefresher {

    private val refreshers = sources.filterIsInstance<ConfigRefresher>()

    private val _generation = MutableStateFlow(0L)
    override val generation: StateFlow<Long> = _generation.asStateFlow()

    override fun boolean(key: String): Boolean? = first { it.boolean(key) }

    override fun int(key: String): Int? = first { it.int(key) }

    override fun long(key: String): Long? = first { it.long(key) }

    override fun double(key: String): Double? = first { it.double(key) }

    override fun string(key: String): String? = first { it.string(key) }

    /**
     * Refreshes every backend, then re-totals the counter.
     *
     * Each is guarded so one unreachable backend cannot stop the others being asked. The
     * interface already says [refresh] must not throw, and both implementations honour it,
     * but a backend silently never refreshing is the failure this class exists to end.
     */
    override suspend fun refresh() {
        refreshers.forEach { refresher ->
            try {
                refresher.refresh()
            } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
                throw cancellation
            } catch (e: Throwable) {
                onRefreshFailure(e)
            }
        }
        // The sum, not an increment: a backend that activated new values has already bumped
        // its own counter, and totalling them means the chain's counter cannot drift from
        // what the backends actually did.
        _generation.value = sources.sumOf { it.generation.value }
    }

    private fun <T : Any> first(read: (ConfigSource) -> T?): T? = sources.firstNotNullOfOrNull(read)
}
