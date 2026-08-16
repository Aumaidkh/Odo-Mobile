package com.hopcape.odo.core.domain.refuel

/**
 * The payment apps detection is willing to read, and the only ones it ever will.
 *
 * A fixed list rather than a scan of what is installed. Asking the package manager which
 * payment apps a phone has means declaring `<queries>` for each of them, which is a second
 * thing a listing review asks about on top of the notification permission — and it buys
 * nothing, because a package that is not installed simply never posts a notification for the
 * listener to match.
 *
 * The consequence is that the settings screen may list an app the owner does not have. That
 * is the honest trade: the list says what Odo *would* read, and every entry can be switched
 * off. An empty list, which is what shipped before this existed, means detection can never
 * fire at all.
 */
object PaymentApps {

    /** Google Pay (the India build — the international one posts nothing comparable). */
    const val GOOGLE_PAY = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE = "com.phonepe.app"
    const val PAYTM = "net.one97.paytm"

    /**
     * The shell, so `adb shell cmd notification post` can drive the whole path on a debug
     * build. It posts as `com.android.shell`, which is not a payment app and must never be
     * readable in a build an owner installs — [defaultsFor] is what keeps it out.
     */
    const val SHELL = "com.android.shell"

    /** What a phone starts with, before the owner has switched anything off. */
    val known: List<String> = listOf(GOOGLE_PAY, PHONEPE, PAYTM)

    /**
     * The packages to register on this build.
     *
     * [includeShell] is true only on a debug build. Wiring it to the build variant rather
     * than to a setting is deliberate: a switch could be turned on in a release, and "Odo can
     * read anything the shell posts" is not a state to leave reachable.
     */
    fun defaultsFor(includeShell: Boolean): List<String> =
        if (includeShell) known + SHELL else known
}
