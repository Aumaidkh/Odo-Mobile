package com.hopcape.odo.infrastructure.supabase.auth

/**
 * The one account this build signs in as.
 *
 * **Temporary, and deliberately the only file that knows these.** Phone OTP is the real
 * plan (PRD, TDD §6.1); until it lands, sync needs *a* real session, because `owner_id` is
 * stamped server-side from `auth.uid()` and an anonymous client cannot write a single row.
 * A fixed development account is the cheapest way to get one and prove the whole path —
 * RLS, owner stamping, adoption — works end to end.
 *
 * Replacing this with phone auth is: delete this file, and have [SupabaseSessionManager]
 * take the credentials it is given instead of reading them from here. Nothing else in the
 * sync path knows a password exists.
 *
 * These are committed, which is the trade being made knowingly: it is a throwaway account
 * on a development project, guarded by the same RLS as any other, and it is worth nothing
 * to anyone without also having the project URL. Do not point this at a project holding
 * real owners' data, and do not follow this pattern for anything else — every actual secret
 * (`service_role`, `ANTHROPIC_API_KEY`, Razorpay) stays in Edge Function environments.
 */
internal object DevCredentials {
    const val EMAIL = "aumaid@gmail.com"
    const val PASSWORD = "password123"
}
