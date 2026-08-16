package com.hopcape.odo.core.domain.showcase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one-at-a-time rule for coach marks (#227).
 *
 * A surface whose due-condition holds asks for the grant; this answers. Exactly one hook
 * holds the grant at any moment across the whole app — two coach marks racing for the
 * same frame is the failure that makes the pattern feel broken, and no individual screen
 * can prevent it because Home can plausibly have two hooks due on the same launch.
 *
 * **No chaining.** A denied request is not queued and a released grant is not handed to
 * whoever asked second — back-to-back coach marks read as a carousel, which this epic
 * deliberately is not. The contract with callers: request once per visit to the surface;
 * a denial means "not this visit", and the hook's next chance is the surface's next visit.
 *
 * Three ways a grant ends, two of which write the seen record:
 * - [dismissed] — the owner tapped it away. Seen: a read tip is a seen tip.
 * - [actedOn] — the owner tapped through. Seen, for the same reason.
 * - [surfaceLeft] — the surface disposed while the mark was up (a redirect navigated
 *   away, the tab switched). Not seen: the owner never answered, so the hook keeps its
 *   one showing for a calmer moment.
 */
class ShowcaseArbiter(
    private val store: ShowcaseSeenStore,
) {
    private val mutex = Mutex()

    private val _active = MutableStateFlow<ShowcaseHookId?>(null)

    /** The hook currently holding the grant, `null` when nothing shows. */
    val active: StateFlow<ShowcaseHookId?> = _active.asStateFlow()

    /**
     * Ask to show [hook] now. True = granted, the surface renders its coach mark and owes
     * exactly one of [dismissed], [actedOn] or [surfaceLeft] back.
     */
    suspend fun request(hook: ShowcaseHookId): Boolean = mutex.withLock {
        when {
            _active.value != null -> false
            store.isSeen(hook) -> false
            else -> {
                _active.value = hook
                true
            }
        }
    }

    /** The owner tapped the mark away. Seen forever, grant released, nothing promoted. */
    suspend fun dismissed(hook: ShowcaseHookId) = settle(hook)

    /** The owner tapped through to the thing being taught. Seen forever, same as [dismissed]. */
    suspend fun actedOn(hook: ShowcaseHookId) = settle(hook)

    /**
     * The surface disposed while [hook] held the grant — releases it without writing
     * seen, so the hook can fire again on a visit the owner actually stays for. No-op if
     * [hook] is not the active one, so a stale disposal cannot clobber a newer grant.
     */
    fun surfaceLeft(hook: ShowcaseHookId) {
        _active.compareAndSet(hook, null)
    }

    private suspend fun settle(hook: ShowcaseHookId) = mutex.withLock {
        store.markSeen(hook)
        _active.compareAndSet(hook, null)
    }
}
