package com.hopcape.logging.internal.sinks

/**
 * Implemented by a [LogSink] that owns a rotatable file — currently only [FileSink]. Lets
 * [AsyncSink] finalize the current file on an **explicit** [LogSink.flush], without adding
 * anything to the public `Logger`/`LogSink` surface: this stays an internal detail two sinks
 * in the same module agree on.
 *
 * Why a marker interface rather than folding this into [LogSink.flush] itself: `flush()` also
 * runs on the size/time/level triggers that fire many times a minute, and rotating the file
 * that often would defeat the point of having separate, much coarser rotation thresholds
 * (2 MB / UTC midnight). Only an *explicit* flush — `Logger.flush()`, which in production
 * means `ProcessLifecycleOwner.onStop` or the upload coordinator preparing to read sealed
 * files — should also conclude the file.
 */
internal interface Sealable {
    /** Seals whatever is currently open, if anything is. A no-op if nothing has been written
     *  since the last seal. */
    fun sealCurrentFile()
}
