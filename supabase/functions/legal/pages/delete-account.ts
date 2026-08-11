// The account-deletion page — the URL on the Play Store listing.
//
// Google requires that an owner can delete their account from the web, without installing the
// app, and that the page actually does something. This one does the real thing: verify the
// number by SMS, then erase.
//
// The verification is Firebase phone auth, the same mechanism the app signs in with, for one
// reason — the account IS the phone number, so proving the number is the only way to prove
// the account. That makes the page a small Firebase web client:
//
//   1. The visitor types a number and Firebase sends a code (invisible reCAPTCHA gates it).
//   2. The code is confirmed, which signs the visitor in and yields a Firebase ID token.
//   3. That token is POSTed back to this same URL. The server verifies it and erases
//      everything on the Supabase side (see `../erase.ts`).
//   4. Once the server confirms, the browser deletes the Firebase user too. Client-side,
//      because `deleteUser` only needs a recent sign-in — which we just did — and that saves
//      putting a Google service-account key in the function's secrets.
//
// Two things must be set up in the Firebase console or this page silently fails; both are in
// `supabase/README.md`: a registered Web App (for the config below) and this function's host
// added to Authentication → Settings → Authorized domains.

import { IDENTITY } from '../identity.ts'
import { page } from './layout.ts'

/**
 * What the page may load. Wider than the other two because Firebase phone auth pulls the SDK
 * from gstatic, runs reCAPTCHA out of google.com, and talks to the Identity Toolkit.
 *
 * Wildcarded at the subdomain level deliberately: reCAPTCHA and Firebase both move between
 * hosts by region and by SDK version, and a policy pinned to today's exact hostnames would
 * break this page — the one page that must not break — on somebody else's deploy schedule.
 */
export const DELETE_PAGE_CSP = [
  "default-src 'none'",
  "script-src 'unsafe-inline' https://*.gstatic.com https://*.google.com https://*.googleapis.com",
  // supabase.co is here for the static copy of this page: served from another host, the erase
  // POST is cross-origin, and 'self' would refuse it.
  "connect-src 'self' https://*.supabase.co https://*.googleapis.com https://*.google.com https://*.gstatic.com",
  "frame-src https://*.google.com https://*.firebaseapp.com",
  "style-src 'unsafe-inline'",
  'img-src data: https://*.gstatic.com https://*.google.com',
  "form-action 'none'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
].join('; ')

/** Shown instead of the form when `FIREBASE_WEB_CONFIG` is not set on the function. */
const notConfigured = `
<div class="card warn">
  <h3>This page is not finished being set up</h3>
  <p>
    We cannot verify your number right now, so deletion cannot be completed here. Email
    <a href="mailto:${IDENTITY.supportEmail}">${IDENTITY.supportEmail}</a> from any address, with the phone
    number on the account, and we will delete it for you within 7 days.
  </p>
</div>`

const form = `
<div class="card">
  <!-- Step 1 — the number -->
  <div class="step active" id="step-phone">
    <label for="phone">Phone number on the account</label>
    <input id="phone" type="tel" inputmode="tel" autocomplete="tel" value="+91 " placeholder="+91 98765 43210">
    <p class="note" style="margin-top:.6rem">Include the country code. We will send a 6-digit code by SMS.</p>
    <div class="actions">
      <button id="send">Send code</button>
    </div>
  </div>

  <!-- Step 2 — the code -->
  <div class="step" id="step-code">
    <label for="code">Enter the 6-digit code sent to <span id="sent-to"></span></label>
    <input id="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000">
    <div class="actions">
      <button id="verify">Verify</button>
      <button id="back" class="quiet" type="button">Use a different number</button>
    </div>
  </div>

  <!-- Step 3 — the deliberate, destructive bit -->
  <div class="step" id="step-confirm">
    <h3>Number verified</h3>
    <p>Deleting the account for <strong id="confirm-number"></strong> removes:</p>
    <ul>
      <li>your profile, name, photo and settings</li>
      <li>every car, service record, bill and bill photo</li>
      <li>every document you uploaded, including insurance and registration files</li>
      <li>your reminders, health scores and cost history</li>
    </ul>
    <p class="note">
      This runs immediately. There is no undo, no grace period, and we cannot recover any of it
      afterwards. Download anything you need first.
    </p>
    <label class="check">
      <input type="checkbox" id="understood">
      <span>I understand this permanently deletes my ${IDENTITY.product} account and all of its data.</span>
    </label>
    <div class="actions">
      <button id="destroy" class="danger" disabled>Delete my account permanently</button>
    </div>
  </div>

  <!-- Step 4 — done -->
  <div class="step" id="step-done">
    <h3>Deleted</h3>
    <p id="done-text"></p>
    <p class="note">
      If the app is still installed on a phone, open <strong>Profile → Delete my data</strong> or uninstall
      it to clear the copy stored there.
    </p>
  </div>

  <div class="msg" id="msg"></div>
  <div id="recaptcha"></div>
</div>`

/**
 * @param webConfig the Firebase web config as a JSON object literal, or null when the page is
 *   not configured to verify anybody.
 * @param eraseEndpoint where the ID token is POSTed. Defaults to this page's own path, which
 *   is right when the function serves the page itself. A static copy on another host has to
 *   pass the function's absolute URL instead, because its own path is a file with nothing
 *   behind it.
 */
export const deleteAccountPage = (webConfig: string | null, eraseEndpoint?: string): string =>
  page({
    title: 'Delete your account',
    description: `Permanently delete your ${IDENTITY.product} account and all of its data. Verify your phone number by SMS — no app install required.`,
    active: 'delete-account',
    head: webConfig
      ? `<link rel="preconnect" href="https://www.gstatic.com" crossorigin>
<link rel="preconnect" href="https://identitytoolkit.googleapis.com" crossorigin>`
      : '',
    body: `
<h1>Delete your account</h1>
<p class="lede">
  This deletes your ${IDENTITY.product} account and everything stored under it, for good. You do not need the
  app installed — verifying the phone number on the account is enough.
</p>

${webConfig ? form : notConfigured}

<h2>What survives, and why</h2>
<p>
  Nothing that identifies you. When a bill is price-checked, the amount, the service category
  and the city join a shared pool that carries no account, car or bill reference — it is what
  lets the next owner know what a service should cost. Those points cannot be traced back to
  you and are not removed. Everything else goes.
</p>

<h2>Prefer to ask us?</h2>
<p>
  Email <a href="mailto:${IDENTITY.supportEmail}">${IDENTITY.supportEmail}</a> from any address, telling us the phone
  number on the account. We verify the number before acting and complete the deletion within
  7 days. The form above is faster and does not involve a human reading your details.
</p>
${webConfig ? script(webConfig, eraseEndpoint) : ''}
`,
  })

/**
 * The client half of the flow.
 *
 * The browser code below concatenates strings rather than using JavaScript template literals,
 * because it lives inside a TypeScript one — every backtick and interpolation in it would
 * otherwise need escaping, and one missed escape is a broken page for somebody trying to
 * exercise a legal right.
 *
 * @param webConfig the Firebase web config as a JSON object literal, already validated by the
 *   caller. Rendered straight into the module, which is safe: it holds only public client
 *   identifiers, and it never reaches this function unless it parsed as JSON.
 * @param eraseEndpoint absolute URL of the erase endpoint, or undefined to POST to this
 *   page's own path.
 */
const script = (webConfig: string, eraseEndpoint?: string): string => `
<script type="module">
// Re-serialised JSON, with < neutralised so a stray closing script tag inside a value
// cannot end this block early.
const CONFIG = ${webConfig.replace(/</g, '\\u003c')};
const ERASE_ENDPOINT = ${eraseEndpoint ? JSON.stringify(eraseEndpoint).replace(/</g, '\\u003c') : 'location.pathname'};

import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {
  getAuth, RecaptchaVerifier, signInWithPhoneNumber, deleteUser,
} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';

const auth = getAuth(initializeApp(CONFIG));
auth.useDeviceLanguage();

const el = (id) => document.getElementById(id);
const msg = el('msg');

const show = (id) => {
  for (const step of document.querySelectorAll('.step')) step.classList.remove('active');
  el(id).classList.add('active');
};

const say = (text, kind) => {
  msg.textContent = text;
  msg.className = 'msg show ' + kind;
};

const clearSay = () => { msg.className = 'msg'; };

const busy = (button, label) => {
  button.disabled = true;
  button.dataset.label = button.textContent;
  button.textContent = label;
};

const idle = (button) => {
  button.disabled = false;
  if (button.dataset.label) button.textContent = button.dataset.label;
};

// Firebase's codes are precise but unreadable. Anything unmapped falls through to a message
// that at least tells the reader what to do next.
const FIREBASE_ERRORS = {
  'auth/invalid-phone-number': 'That number does not look right. Include the country code, like +91 98765 43210.',
  'auth/missing-phone-number': 'Enter the phone number on the account.',
  'auth/quota-exceeded': 'Too many codes have been sent today. Please try again tomorrow.',
  'auth/too-many-requests': 'Too many attempts from this device. Wait a few minutes and try again.',
  'auth/invalid-verification-code': 'That code is wrong. Check the SMS and try again.',
  'auth/code-expired': 'That code has expired. Go back and request a new one.',
  'auth/captcha-check-failed': 'The security check failed. Reload the page and try again.',
  'auth/unauthorized-domain': 'This page is not authorised to send codes. Please email us instead.',
  'auth/operation-not-allowed': 'Phone sign-in is switched off. Please email us instead.',
  'auth/network-request-failed': 'No connection. Check your network and try again.',
  // The two the Firebase project itself can be wrong in. Both read the same to a visitor —
  // nothing they did, nothing they can retry — but they say enough to be searched for, and
  // the generic message they used to produce said nothing at all.
  'auth/configuration-not-found': 'Verification is not set up on this project yet. Please email us instead.',
  'auth/billing-not-enabled': 'Verification is unavailable right now. Please email us instead.',
};

const SERVER_ERRORS = {
  invalid_token: 'We could not confirm your verification. Please start again.',
  no_phone_claim: 'We could not confirm your verification. Please start again.',
  stale_verification: 'That verification is too old. Please start again.',
  erase_failed: 'Something went wrong while deleting. Nothing partial was left behind — please email us.',
  not_configured: 'Deletion is temporarily unavailable. Please email us.',
};

const explain = (error) =>
  FIREBASE_ERRORS[error && error.code] ||
  'Something went wrong. Please try again, or email ' + ${JSON.stringify(IDENTITY.supportEmail)} + '.';

// E.164: a plus and 8 to 15 digits. Everything a person might type in between - spaces,
// dashes, brackets - is dropped first.
const normalise = (raw) => {
  const digits = raw.replace(/[^0-9+]/g, '');
  const e164 = digits.startsWith('+') ? '+' + digits.slice(1).replace(/\\+/g, '') : '';
  return /^\\+[0-9]{8,15}$/.test(e164) ? e164 : null;
};

let verifier = null;
let confirmation = null;
let number = null;

// The reCAPTCHA is invisible and bound to the send button. It is rebuilt after a failure
// because a spent widget cannot be reused, and reusing one is what makes a second attempt
// fail with captcha-check-failed for no visible reason.
const recaptcha = () => {
  if (!verifier) verifier = new RecaptchaVerifier(auth, 'recaptcha', { size: 'invisible' });
  return verifier;
};

const resetRecaptcha = () => {
  if (verifier) { try { verifier.clear(); } catch (_) { /* already gone */ } }
  verifier = null;
  el('recaptcha').innerHTML = '';
};

el('send').addEventListener('click', async () => {
  clearSay();
  const candidate = normalise(el('phone').value);
  if (!candidate) {
    say('Enter the number with its country code, like +91 98765 43210.', 'error');
    return;
  }
  busy(el('send'), 'Sending…');
  try {
    confirmation = await signInWithPhoneNumber(auth, candidate, recaptcha());
    number = candidate;
    el('sent-to').textContent = candidate;
    el('confirm-number').textContent = candidate;
    show('step-code');
    el('code').focus();
  } catch (error) {
    resetRecaptcha();
    say(explain(error), 'error');
  } finally {
    idle(el('send'));
  }
});

el('back').addEventListener('click', () => {
  clearSay();
  confirmation = null;
  resetRecaptcha();
  el('code').value = '';
  show('step-phone');
});

el('verify').addEventListener('click', async () => {
  clearSay();
  const code = el('code').value.replace(/[^0-9]/g, '');
  if (code.length !== 6) {
    say('Enter the 6-digit code from the SMS.', 'error');
    return;
  }
  busy(el('verify'), 'Checking…');
  try {
    await confirmation.confirm(code);
    show('step-confirm');
  } catch (error) {
    say(explain(error), 'error');
  } finally {
    idle(el('verify'));
  }
});

el('understood').addEventListener('change', (event) => {
  el('destroy').disabled = !event.target.checked;
});

el('destroy').addEventListener('click', async () => {
  clearSay();
  busy(el('destroy'), 'Deleting…');

  let outcome;
  try {
    const user = auth.currentUser;
    if (!user) throw new Error('signed out');

    // Freshly minted rather than cached: the server rejects a verification older than a few
    // minutes, and a token issued at sign-in can be stale by the time someone reads the
    // warning and ticks the box.
    const idToken = await user.getIdToken(true);

    const response = await fetch(ERASE_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken }),
    });
    const payload = await response.json().catch(() => ({}));

    if (!response.ok) {
      say(SERVER_ERRORS[payload.error_code] || 'Deletion failed. Please email us and we will do it by hand.', 'error');
      idle(el('destroy'));
      return;
    }
    outcome = payload.status;

    // The Supabase side is gone. Remove the Firebase record too, so the number itself is no
    // longer held anywhere. deleteUser needs a recent sign-in, which the code just provided.
    try { await deleteUser(user); } catch (_) { /* server side already done; not worth blocking on */ }
  } catch (error) {
    say(explain(error), 'error');
    idle(el('destroy'));
    return;
  }

  el('done-text').textContent = outcome === 'no_account'
    ? 'There was no ' + ${JSON.stringify(IDENTITY.product)} + ' account for ' + number + ', so there was nothing to delete. The number is not stored with us.'
    : 'The account for ' + number + ' and everything stored under it has been permanently deleted.';
  show('step-done');
});

// Enter should submit the step you are on, not reload the page.
for (const [input, button] of [['phone', 'send'], ['code', 'verify']]) {
  el(input).addEventListener('keydown', (event) => {
    if (event.key === 'Enter') { event.preventDefault(); el(button).click(); }
  });
}
</script>`
