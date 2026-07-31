package com.hopcape.odo.core.domain.document.model

/**
 * How a document's file reached the vault.
 *
 * Kept because it is the only honest basis for the vault's **Verified** badge. Every
 * document has a file — unlike a service log, where the presence of a bill photo is
 * itself the evidence — so "verified" cannot mean "has a file". What it can mean is
 * provenance: a copy pulled from DigiLocker came from the issuing authority, while a
 * photo or a PDF the owner picked came from the owner. That distinction is known at
 * capture time and never inferred, which is what keeps the badge truthful.
 *
 * The service-log analog of [com.hopcape.odo.core.domain.servicelog.model.LogSource].
 */
enum class DocumentSource {

    /** Photographed in-app; expiry read by the scanner or typed by the owner. */
    SCANNED,

    /** A PDF or image picked from the device. */
    UPLOADED,

    /** Pulled from DigiLocker — an official copy, so it earns the Verified badge. */
    DIGILOCKER;

    /**
     * True only for a copy that came from the issuer. Read by the vault's and the resale
     * passport's badges, which must never call an owner-supplied file verified.
     */
    val isVerified: Boolean get() = this == DIGILOCKER
}
