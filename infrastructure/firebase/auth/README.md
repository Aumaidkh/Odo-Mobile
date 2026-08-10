# `:infrastructure:firebase:auth`

Firebase Phone Auth behind `:core:domain`'s `PhoneVerifier` port.

## Why Firebase is here at all

Sign-in has never worked against a real number. Supabase relays SMS through Twilio,
MessageBird or Vonage, and all three need TRAI DLT registration in India — an entity, a header
and a message template approved on a carrier portal before anything is delivered. Firebase runs
its own registered senders, so there is no portal to clear.

Firebase can only prove the number, though. Every `owner_id` in this project is a `uuid`
referencing `auth.users(id)`, and a Firebase UID is a 28-character string, so a Firebase token
cannot be the one row-level security checks. This module hands back a Firebase ID token;
`:infrastructure:supabase` trades it for a real Supabase session through the `firebase-session`
Edge Function (see `supabase/README.md`).

## Shape

- `PhoneVerifier` — the port, in `:core:domain`, so `:infrastructure:supabase` can compose it
  without importing Firebase.
- `UnavailablePhoneVerifier` (commonMain) — what `firebaseAuthModule` binds. iOS keeps it.
- `FirebasePhoneVerifier` (androidMain) — the real one, bound by `firebaseAuthAndroidModule`,
  which `OdoApplication` includes in its platform module. That is not stylistic:
  `verifyPhoneNumber` has no Activity-free overload, and only the app bootstrap can supply one.

iOS is deliberately unimplemented. v1.0 is Android, and phone auth on iOS needs an APNs auth
key in the Firebase console, silent-push handling in the AppDelegate and a reCAPTCHA fallback.

## Turning it on

Nothing below is code. Until all of it is done, leave `supabase.phoneAuth` off — the app signs
in as the fixed development account, which still produces a real JWT under real row-level
security.

1. **Blaze plan.** SMS verification has needed a billing account since September 2024; it is
   not available on Spark. Roughly $0.01 per verification in India, first 10 a day free.
2. **Authentication → Sign-in method → Phone → Enable.**
3. **SHA fingerprints.** Project settings → Your apps → add the SHA-1 *and* SHA-256 of every
   signing config: `debug`, `stage` and `release`. Without them Play Integrity fails and every
   send drops to a reCAPTCHA web view — it still works, but it is a visibly worse flow and the
   first sign that this step was skipped.
   ```bash
   ./gradlew :androidApp:signingReport
   ```
   Re-download `google-services.json` afterwards.
4. **Test numbers.** Authentication → Sign-in method → Phone → Phone numbers for testing. Add
   one with a fixed code. The instrumented suite uses it, so CI never sends a real SMS and
   never spends anything.
5. **The Edge Function and its SQL** — `supabase/README.md`.
6. `supabase.phoneAuth=true` in `local.properties`, then rebuild.

## Things that will bite

- **Do not add `requireSmsValidation(true)`.** It reads exactly like the switch that forces a
  real SMS, and it is multi-factor only: for first-factor sign-in `PhoneAuthOptions.build()`
  throws `IllegalArgumentException`, "You cannot require sms validation without setting a
  multi-factor session", before anything is sent.
- **Instant verification is a real path, not a defensive one.** Firebase may verify a number
  on the same device with no SMS at all, in which case `onCodeSent` never fires and
  `onVerificationCompleted` is the only callback that arrives. `FirebasePhoneVerifier` resumes
  on it so nothing hangs, and `submitCode` falls back to the credential it carries. The owner
  still waits on a code screen for a message that is not coming, then types six digits that
  are ignored. Fixing that means giving `PhoneVerifier` a way to say "already verified", which
  is a change to `:feature:auth`.
- **A number Firebase rejects and a wrong code are different answers.** The mapping in
  `FirebasePhoneVerifier` keeps `InvalidOtp` (retype) apart from `OtpExpired` (resend), because
  the screen offers a different action for each.
- **The verifier is a `single` for a reason.** It holds the verification id between
  `startVerification` and `submitCode`; a new instance per call would lose it and every typed
  code would look expired.
- **The app's own SMS auto-read still runs.** `OtpViewModel` listens through SMS Retriever
  independently of Firebase's auto-retrieval. Both are best-effort and neither is required —
  the typed code always works.
