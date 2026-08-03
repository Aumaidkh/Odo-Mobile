package com.hopcape.odo.core.data.sync

/**
 * A push the server answered with a refusal, and whether sending it again could ever help.
 *
 * Implemented by the failures a remote data source throws, so the sync bookkeeping can tell
 * "the server was down" apart from "the server will never accept this" without knowing which
 * vendor's client threw. `:core:data` holds it because that is where the bookkeeping is and
 * the layer both the engine and the adapters can see.
 *
 * The distinction is not cosmetic. Both cases leave the rows `PENDING` and both get retried
 * with growing backoff, so a permanently-refused row is retried forever and reads as "still
 * working on it" — for days, with nothing saying otherwise. Recording which one it was is
 * what makes that visible.
 */
interface SyncRejection {

    /** The HTTP status the server answered with. */
    val status: Int

    /** `true` when the same request will get the same refusal however often it is sent. */
    val isPermanent: Boolean
}
