package com.hopcape.odo.core.navigation

/**
 * Translate a [NavigationCommand] into concrete [Navigator] back-stack operations.
 *
 * This is the *only* place commands meet the back stack — it lives in the
 * navigation core and runs inside the host, so features stay ignorant of how their
 * intents are carried out. Kept `internal`; the host collects the command bus and
 * calls this.
 */
internal fun Navigator.execute(command: NavigationCommand) {
    when (command) {
        is NavigationCommand.NavigateTo -> {
            command.popUpTo?.let { popUpTo(it, command.inclusive) }
            val alreadyOnTop = command.singleTop && backStack.lastOrNull() == command.destination
            when {
                alreadyOnTop -> Unit

                // The same key twice is not two screens — it is a crash. Nav3 keys saved state
                // by the destination itself, so a back stack holding one key in two places
                // throws out of `SaveableStateHolder` ("Key … was used multiple times") and
                // takes the app down. A detected-fill notification did exactly that: its
                // launch intent was replayed on an activity recreation and pushed a
                // `Refuel.Confirm` that was already further down the stack.
                //
                // That cause is fixed where it belongs, by consuming the intent. This is the
                // backstop, and it is not a behaviour change — pushing a duplicate key was
                // never something the framework could represent. Bringing the existing entry
                // forward is the only meaning the request can have, and it keeps whatever
                // state that entry had.
                backStack.contains(command.destination) ->
                    popUpTo(command.destination, inclusive = false)

                else -> navigate(command.destination)
            }
        }

        is NavigationCommand.FinishFlow -> {
            popFlow(command.belongsToFlow)
            // The flow may own the root itself — first run is rooted at one of the intros.
            // popFlow stops there (goBack never removes the root), so drop it explicitly:
            // emptying the stack is fine here and only here, because a destination is
            // pushed on the next line. LeaveFlow has no landing to push, which is why it
            // keeps the stop-at-root behaviour.
            val root = backStack.singleOrNull()
            if (root != null && command.belongsToFlow(root)) popUpTo(root, inclusive = true)
            if (backStack.lastOrNull() != command.destination) navigate(command.destination)
        }

        is NavigationCommand.LeaveFlow -> popFlow(command.belongsToFlow)

        NavigationCommand.Back -> goBack()
    }
}

/**
 * Drop every entry at the top of the stack that [belongsToFlow] accepts.
 *
 * Top-down rather than by key: a flow's own steps are what has to go, and the first entry
 * below them is whatever opened the flow — which the flow itself does not know. `goBack`
 * stops at the root, so this always terminates.
 */
private fun Navigator.popFlow(belongsToFlow: (OdoDestination) -> Boolean) {
    while (canGoBack && belongsToFlow(backStack.last())) goBack()
}
