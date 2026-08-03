package com.hopcape.odo.core.platform.sms

/**
 * The eleven-character hash Android's SMS Retriever requires at the end of the message body.
 *
 * This exists because the failure it prevents is invisible. The retriever only delivers a
 * message whose text carries the hash of the signing certificate of the app asking for it, so
 * a message sent with the wrong hash is never delivered — and from inside the app that is
 * indistinguishable from an SMS that never arrived. No error, no callback, nothing to debug.
 *
 * The consequence worth writing down: **debug and release builds are signed differently and
 * therefore have different hashes.** Auto-read working in development says nothing about
 * release, and the SMS template on the server has to carry both. Play App Signing makes it
 * worse — the certificate that signs the uploaded artifact is not the one that signs what
 * users install, so the hash to use is the one from the *distributed* build.
 *
 * Read it with [current] and put the value in the provider's template.
 */
interface SmsAppSignature {

    /**
     * The hashes this build could be delivered against, or empty where the concept does not
     * apply.
     *
     * A list rather than one value, because an app can be signed by more than one certificate.
     */
    suspend fun current(): List<String>
}
