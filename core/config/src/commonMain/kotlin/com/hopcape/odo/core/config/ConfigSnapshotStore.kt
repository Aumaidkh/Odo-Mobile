package com.hopcape.odo.core.config

/**
 * The last set of remote values this device saw, kept across launches.
 *
 * **Why a backend needs one.** The Firebase SDK cached its own values on disk, so a
 * cold start with no network still resolved the last known config. A backend that is
 * a plain HTTP read has no such cache, and without this every launch would resolve
 * to compiled defaults until the first fetch landed — which for a kill switch means
 * the thing being killed runs for the first few seconds of every launch. That is not
 * a kill switch.
 *
 * Raw strings, the same form [Value.default] is written in and the same form
 * [LocalConfigOverrides] holds, so one parsing path serves all three.
 *
 * A read must never throw. A store that cannot be read is a device with nothing
 * remembered, which resolves to compiled defaults — the same outcome as a fresh
 * install, and a correct one.
 */
interface ConfigSnapshotStore {

    fun read(): Map<String, String>

    fun write(values: Map<String, String>)

    /**
     * Remembers nothing.
     *
     * The binding on a platform with no store, and in tests. Named rather than a
     * lambda so a graph missing its real store fails as an obvious no-op instead of
     * as a null.
     */
    object None : ConfigSnapshotStore {
        override fun read(): Map<String, String> = emptyMap()
        override fun write(values: Map<String, String>) = Unit
    }
}
