package com.hopcape.odo.core.platform.app

/**
 * One id for this installation of the app, stable across restarts.
 *
 * It exists so every diagnostic signal from one phone can be grouped: log files upload under
 * it, and the reference code a support ticket quotes is derived from it. Before this, log
 * uploads used a per-process random id, so the same phone landed in a new folder on every
 * cold start and nothing could be read as one history.
 *
 * Generated on first read and kept in plain device storage, not `SecureStore`: it is not a
 * secret, it names no person, and it is not tied to an account. It is cleared by an app
 * uninstall or a data clear, which is also what "forget this device" has to mean.
 */
interface InstallationId {

    /** The id. The same string for the life of the installation. */
    val value: String
}
