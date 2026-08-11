package com.hopcape.odo.core.domain.legal

/**
 * Where Odo's published legal documents live.
 *
 * A port because two things answer it. The addresses are **configured remotely** so a move to
 * a custom domain, or a correction to a published URL, does not need an app release — a legal
 * notice nobody can reach is the one kind of bug a store review will find. Behind that sits a
 * build-time answer derived from the deployed backend, for the launch before the first fetch
 * lands and for a device that never reaches Firebase at all.
 *
 * The pages themselves are served by an Edge Function (`supabase/functions/legal`) rather
 * than a static host, because the deletion page has to prove who is asking and then actually
 * delete something.
 *
 * Plain strings, not a URL type: every caller hands them straight to the platform's link
 * opener, and there is nothing here to parse or validate on the way. A blank string means
 * "not configured", and callers leave the row out rather than offering a dead link.
 */
interface LegalLinks {

    /** The full Privacy Policy. Summarised in-app; this is the authoritative text. */
    val privacyPolicy: String

    /** The Terms of Use. */
    val termsOfUse: String

    /**
     * The web account-deletion page, the address the Play Store listing points at.
     *
     * The app does not send anyone here — it deletes accounts in-app. It is published so
     * that someone who has uninstalled Odo still has a way to erase what it holds.
     */
    val deleteAccount: String

    companion object {
        /**
         * Koin qualifier for the build-time answer, the one the remotely-configured
         * implementation falls back to.
         *
         * A qualifier rather than two types because they are the same contract answered from
         * different places, and the decorator has to resolve the thing it decorates — Koin's
         * later-definition-wins override leaves no way to reach a replaced binding otherwise.
         */
        const val BUILT_IN: String = "legal-links-built-in"
    }
}
