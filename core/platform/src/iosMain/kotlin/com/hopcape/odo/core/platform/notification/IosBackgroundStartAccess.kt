package com.hopcape.odo.core.platform.notification

/**
 * iOS has no equivalent restriction to warn about, and nothing to open. Detection does not run
 * there at all, so this is never consulted — it exists so the graph is the same on both
 * platforms.
 */
internal class IosBackgroundStartAccess : BackgroundStartAccess {

    override fun needsAttention(): Boolean = false

    override fun open(): Boolean = false
}
