package com.hopcape.odo.feature.support.presentation

/**
 * Which revision of the legal documents this build's summary was written against.
 *
 * Hand-maintained, and it has to stay in step with `IDENTITY.lastUpdated` in
 * `supabase/functions/legal/identity.ts`. Two reasons it is not fetched:
 *
 * - The screen has to render with no network. Someone reading a privacy notice is often
 *   doing it because they are about to leave, and that is the worst moment for a spinner.
 * - A build timestamp would move the date on every unrelated redeploy, which is exactly the
 *   signal a reader uses to decide whether what they agreed to still applies.
 *
 * [SUMMARY_VERSION] is the app's own, separate from the date: the in-app summary can be
 * reworded without the hosted policy changing, and a reader comparing the two deserves to
 * see which is which.
 */
internal object PolicyRevision {

    /** The date the hosted Terms and Privacy Policy last changed. */
    const val LAST_UPDATED: String = "10 August 2026"

    /** The revision of the summary on this screen. */
    const val SUMMARY_VERSION: String = "v1.0"
}
