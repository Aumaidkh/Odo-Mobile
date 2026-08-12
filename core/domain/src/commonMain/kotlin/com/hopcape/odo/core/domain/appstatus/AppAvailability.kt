package com.hopcape.odo.core.domain.appstatus

/**
 * What the app shell is allowed to show right now, resolved from [AppStatus] by
 * [AppAvailabilityPolicy]. This — not [AppStatus] — is what the shell, sync, and every
 * other consumer branch on.
 */
sealed interface AppAvailability {

    /** Nothing blocking. The app runs as normal. */
    data object Allowed : AppAvailability

    /**
     * A maintenance window is open but not severe enough to stop local use. Network work
     * (sync, remote calls) should stand down; the local app keeps working.
     */
    data class DegradedByMaintenance(val message: String?) : AppAvailability

    /** The shell has nothing to show but a full-screen stop. */
    sealed interface Blocked : AppAvailability {

        /** This build's version is below the remote minimum. */
        data object UpdateRequired : Blocked

        /** A maintenance window severe enough to stop the app entirely. */
        data class Maintenance(val message: String?) : Blocked
    }
}
