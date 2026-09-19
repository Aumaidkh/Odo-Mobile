package com.hopcape.odo.infrastructure.firebase.crashlytics

import com.hopcape.odo.core.common.runCatchingCancellable

internal actual fun Throwable.restoreFrames(formatted: String) {
    val frames = runCatchingCancellable { formatted.toFrames() }.getOrNull().orEmpty()
    if (frames.isNotEmpty()) stackTrace = frames.toTypedArray()
}

/** The `at com.foo.Bar.baz(Bar.kt:42)` lines, in order, as far as they parse. */
private fun String.toFrames(): List<StackTraceElement> =
    lineSequence()
        .map { it.trim() }
        .filter { it.startsWith(AT_PREFIX) }
        .mapNotNull { it.removePrefix(AT_PREFIX).toFrame() }
        .toList()

private fun String.toFrame(): StackTraceElement? {
    val open = lastIndexOf('(')
    val close = lastIndexOf(')')
    if (open <= 0 || close <= open) return null
    // A Java 9 module prefix ("java.base/java.lang.Thread.run") never appears on Android,
    // but dropping it costs one call and keeps a desktop-recorded trace parseable.
    val qualified = substring(0, open).substringAfterLast('/')
    val lastDot = qualified.lastIndexOf('.')
    if (lastDot <= 0 || lastDot == qualified.lastIndex) return null

    val source = substring(open + 1, close)
    val colon = source.lastIndexOf(':')
    val line = if (colon > 0) source.substring(colon + 1).toIntOrNull() else null
    return StackTraceElement(
        qualified.substring(0, lastDot),
        qualified.substring(lastDot + 1),
        if (line == null) null else source.substring(0, colon),
        line ?: UNKNOWN_LINE,
    )
}

private const val AT_PREFIX = "at "

/** What `StackTraceElement` uses for a frame with no line number, such as a native method. */
private const val UNKNOWN_LINE = -1
