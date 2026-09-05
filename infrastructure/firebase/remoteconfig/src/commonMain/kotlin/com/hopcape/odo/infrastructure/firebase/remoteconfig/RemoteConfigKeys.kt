package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigGroup
import com.hopcape.odo.core.config.Value
import com.hopcape.odo.core.domain.appstatus.MaintenanceSeverity

/**
 * The seven remotely-set keys this module owns.
 *
 * **Why they live here and not beside their ports.** The placement rule is "the lowest
 * module every consumer already depends on". All seven have exactly one consumer each, and
 * all three consumers are in this module. `:core:domain` holds the ports —
 * `AppStatusSource`, `LegalLinks`, `SupportContacts` — and keeps its own rule: no framework
 * types, which a generated Koin module would break.
 *
 * Each group replaces a hand-written set of key constants and a `REMOTE_DEFAULTS` map that
 * had to be kept in step with them, and with a fourth copy in an Android XML resource.
 */
@ConfigGroup("appstatus")
internal interface AppStatusConfig {

    @Value(
        key = "min_supported_version_code",
        default = "0",
        owner = "platform",
        why = "Builds below this are forced to update",
    )
    val minSupportedVersionCode: Long

    /**
     * How hard the current maintenance window bites. One of three
     * [MaintenanceSeverity] names, matched without regard to case:
     *
     * - **`OFF`** — nothing in effect. The default, and where an incident ends.
     * - **`DEGRADED`** — network work stops and sync is held; the app keeps working on what
     *   is already on the device. What a Supabase migration or a broken Edge Function wants.
     * - **`FULL_BLOCK`** — the app stops entirely behind the maintenance screen until this
     *   clears. Reserve it for data that would be corrupted by carrying on.
     *
     * **Anything else reads as `OFF`.** A typo in the console, or a value a later release
     * added that this build has never heard of, fails open rather than blocking everyone —
     * see [RemoteConfigAppStatusSource]. So a value is never partly applied, and a mistyped
     * block is a no-op rather than an outage.
     *
     * [maintenanceMessage] is what the owner is told while this is not `OFF`; blank falls
     * back to the built-in copy.
     */
    @Value(
        key = "maintenance_mode",
        default = "off",
        owner = "platform",
        why = "OFF | DEGRADED (network work stops) | FULL_BLOCK (app stops); anything else reads as OFF",
    )
    val maintenance: MaintenanceSeverity

    @Value(
        key = "maintenance_message",
        default = "",
        owner = "platform",
        why = "What to tell the owner while maintenance is on",
    )
    val maintenanceMessage: String
}

/**
 * The published legal pages.
 *
 * All three default to blank on purpose, and blank is not a placeholder: it means "no
 * override", and [RemoteConfigLegalLinks] then answers with the address the build derives
 * from its own Supabase project. Putting a URL here would freeze one project's address
 * into every build.
 */
@ConfigGroup("legal")
internal interface LegalConfig {

    @Value(
        key = "legal_privacy_policy_url",
        default = "",
        owner = "platform",
        why = "Repoints the privacy page without a release",
    )
    val privacyPolicyUrl: String

    @Value(
        key = "legal_terms_url",
        default = "",
        owner = "platform",
        why = "Repoints the terms page without a release",
    )
    val termsUrl: String

    @Value(
        key = "legal_delete_account_url",
        default = "",
        owner = "platform",
        why = "Repoints the account-deletion page without a release",
    )
    val deleteAccountUrl: String
}

/**
 * Where "Email us" and the feedback forms send their mail.
 *
 * Blank means "no override" here too, and the build's compiled address answers instead.
 * A mailbox that moves would otherwise leave every installed build writing to an address
 * that bounces.
 */
@ConfigGroup("support")
internal interface SupportConfig {

    @Value(
        key = "support_email",
        default = "",
        owner = "platform",
        why = "Moves support to another mailbox without a release",
    )
    val email: String
}
