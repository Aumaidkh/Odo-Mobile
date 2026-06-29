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
            if (!alreadyOnTop) navigate(command.destination)
        }

        NavigationCommand.Back -> goBack()

        is NavigationCommand.BackTo -> popUpTo(command.destination, command.inclusive)

        // Pop to the start destination without hard-coding what it is.
        NavigationCommand.ToRoot -> while (canGoBack) goBack()
    }
}
