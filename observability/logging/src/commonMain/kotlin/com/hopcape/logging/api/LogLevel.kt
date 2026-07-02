package com.hopcape.logging.api

// Public: appears in the `Logger` contract (log(level, ...)), so callers name it.
enum class LogLevel(val priority: Int) {
    VERBOSE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), FATAL(5)
}
