// The shell every page in this function is poured into: one stylesheet, one header, one
// footer, one set of colours.
//
// The look is the Tesla design system: black and white, a premium sans, pill controls, and
// space where decoration would otherwise go. It no longer follows the app's warm palette —
// a reader who taps "Privacy" in the app now arrives somewhere deliberately plainer, which
// is a choice worth knowing about rather than a drift.
//
// **There is no imagery, and there cannot be.** Tesla's own restraint leans on giant product
// photography for its emotion; the CSP here allows `img-src data:` and nothing else, and
// these pages are read on slow connections by people who are cancelling something. So the
// type scale, the black-and-white fields and the spacing carry the weight that photography
// carries there. That is the one place this deviates from the source, and it is deliberate.
//
// No external CSS, no webfont, no analytics. These pages are read once, often on a slow
// connection, sometimes by a Play reviewer, and every request they do not make is one that
// cannot fail. `Gotham` heads the font stack for machines that have it and falls back to
// Inter and the system sans everywhere else, because loading a webfont is a request.

import { IDENTITY } from '../identity.ts'

/** Escape anything interpolated into markup. Only error text needs it today; cheap insurance. */
export const escapeHtml = (value: string): string =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

/** The three pages, in the order the nav shows them. `href` is relative to the function root. */
export const NAV = [
  { href: 'terms', label: 'Terms' },
  { href: 'privacy', label: 'Privacy' },
  { href: 'delete-account', label: 'Delete account' },
] as const

export type PageKey = (typeof NAV)[number]['href'] | 'index'

const STYLES = `
/* ── Tokens ────────────────────────────────────────────────────────────────
   Black and white do the work. Every other colour has to earn its place, and
   only two do: red for a deletion that cannot be undone, green for one that
   succeeded. Controls stay neutral so nothing on the page competes with the
   one sentence it is trying to say.                                        */
:root {
  color-scheme: light dark;

  --white: #FFFFFF;
  --black: #000000;
  --gray-100: #F4F4F4;
  --gray-400: #9CA3AF;
  --gray-700: #374151;

  --bg: var(--white);
  --soft: var(--gray-100);
  --surface: var(--white);
  --border: #E5E7EB;
  --text: var(--black);
  --dim: var(--gray-700);
  --muted: var(--gray-400);

  /* Translucent rather than solid, and blurred: the source treats a control as
     something laid over the page, not cut into it. */
  --btn-bg: rgba(23, 26, 32, .86);
  --btn-fg: var(--white);
  --btn-quiet-bg: rgba(244, 244, 244, .85);
  --btn-quiet-fg: #111827;
  --btn-danger-bg: #B91C1C;
  --btn-danger-fg: var(--white);

  /* The immersive dark panel — the one place a page goes black on purpose. */
  --panel-bg: var(--black);
  --panel-fg: var(--white);
  --panel-border: var(--black);

  --danger: #B91C1C;
  --success: #15803D;
  /* Red at full strength disappears into a black panel, so the panel gets its own. */
  --danger-bright: #F87171;

  --space-2: .5rem;
  --space-4: 1rem;
  --space-6: 2rem;
  --space-8: 3rem;
  --space-10: 4rem;
  --space-12: 6rem;

  --font-sans: Gotham, Inter, "Helvetica Neue", Arial, sans-serif;
  --font-mono: "SF Mono", "Roboto Mono", Menlo, monospace;

  --shadow-card: 0 10px 22px rgba(0, 0, 0, .12);
  --shadow-overlay: 0 18px 34px rgba(0, 0, 0, .18);
}

/* Dark is the same system with the roles swapped, not a second design. */
@media (prefers-color-scheme: dark) {
  :root {
    --bg: var(--black);
    --soft: #111111;
    --surface: #0A0A0A;
    --border: #22262B;
    --text: var(--white);
    --dim: #D1D5DB;
    --muted: var(--gray-400);

    --btn-bg: rgba(255, 255, 255, .92);
    --btn-fg: var(--black);
    --btn-quiet-bg: rgba(255, 255, 255, .08);
    --btn-quiet-fg: var(--white);
    --btn-danger-bg: #DC2626;

    /* A black panel on a black page is not a panel. It lifts instead of drops. */
    --panel-bg: #131313;
    --panel-fg: var(--white);
    --panel-border: #2A2E33;

    --danger: #F87171;
    --success: #4ADE80;

    --shadow-card: 0 10px 22px rgba(0, 0, 0, .5);
    --shadow-overlay: 0 18px 34px rgba(0, 0, 0, .6);
  }
}

* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; }
body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font: 400 16px/1.6 var(--font-sans);
  -webkit-font-smoothing: antialiased;
}

.wrap { max-width: 44rem; margin: 0 auto; padding: 0 var(--space-6) var(--space-10); }

/* ── Header ──────────────────────────────────────────────────────────────
   A wordmark in wide tracking and three quiet links. The current page is
   marked in the text colour, not an accent — the nav is not a place to
   spend attention.                                                        */
header { border-bottom: 1px solid var(--border); background: var(--bg); }
header .wrap { padding-top: var(--space-6); padding-bottom: 0; }
.brand {
  display: inline-block;
  font-size: 1.125rem; font-weight: 600;
  text-transform: uppercase; letter-spacing: .34em;
  color: var(--text); text-decoration: none;
}
nav { display: flex; flex-wrap: wrap; gap: var(--space-6); margin-top: var(--space-6); }
nav a {
  padding-bottom: .85rem; margin-bottom: -1px;
  color: var(--dim); text-decoration: none;
  font-size: 14px; font-weight: 600; letter-spacing: .01em;
  border-bottom: 1px solid transparent;
}
nav a:hover { color: var(--text); }
nav a[aria-current="page"] { color: var(--text); border-bottom-color: var(--text); }

/* ── Type ────────────────────────────────────────────────────────────────
   One message at the top of each page, then body copy that gets out of the
   way. Sections are separated by space rather than rules.                 */
main .wrap { padding-top: var(--space-10); }
h1 {
  font-size: clamp(2rem, 7vw, 2.5rem); font-weight: 700;
  line-height: 1.08; letter-spacing: -.02em;
  margin: 0 0 var(--space-4);
}
h2 {
  font-size: 1.375rem; font-weight: 600; line-height: 1.2; letter-spacing: -.01em;
  margin: var(--space-8) 0 var(--space-2);
}
h3 { font-size: 1rem; font-weight: 600; margin: var(--space-6) 0 var(--space-2); }
p, li { color: var(--text); }
.lede {
  color: var(--dim); font-size: 1.125rem; line-height: 1.5;
  max-width: 34rem; margin: 0 0 var(--space-6);
}
ul, ol { padding-left: 1.2rem; }
li { margin: .4rem 0; }
li > strong { color: var(--text); }
a {
  color: inherit; text-decoration: underline;
  text-underline-offset: 3px; text-decoration-color: var(--muted);
}
a:hover { text-decoration-color: currentColor; }
code {
  font-family: var(--font-mono); font-size: .875em;
  background: var(--soft); border-radius: 4px; padding: .1em .35em;
}

/* ── Surfaces ────────────────────────────────────────────────────────────
   A white card for detail, a dark panel for the thing you must not miss.  */
.card {
  background: var(--surface); border: 1px solid var(--border);
  border-radius: 18px; padding: var(--space-6);
  box-shadow: var(--shadow-card);
  margin: var(--space-6) 0;
}
.card > :first-child { margin-top: 0; }
.card > :last-child { margin-bottom: 0; }

.card.warn {
  background: var(--panel-bg); color: var(--panel-fg);
  border-color: var(--panel-border); border-radius: 24px;
  padding: var(--space-6);
  box-shadow: var(--shadow-overlay);
}
.card.warn p, .card.warn li, .card.warn strong { color: var(--panel-fg); }
.card.warn h2, .card.warn h3 { color: var(--danger-bright); margin-top: 0; }
.card.warn a { text-decoration-color: rgba(255, 255, 255, .5); }
.card.warn code { background: rgba(255, 255, 255, .1); }

table { width: 100%; border-collapse: collapse; margin: var(--space-4) 0; font-size: 14px; }
th, td { text-align: left; vertical-align: top; padding: .7rem .75rem; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 600; font-size: 12px; text-transform: uppercase; letter-spacing: .06em; }
.scroll-x { overflow-x: auto; }

/* ── Controls ────────────────────────────────────────────────────────────
   Pills, quiet, and obvious. A destructive one is the only red on the site. */
label {
  display: block; font-size: 14px; font-weight: 600; letter-spacing: .01em;
  color: var(--dim); margin-bottom: var(--space-2);
}
input[type="text"], input[type="tel"] {
  width: 100%; min-height: 44px; padding: .65rem 14px;
  font: 400 16px/1.2 var(--font-sans);
  color: var(--text); background: var(--surface);
  border: 1px solid var(--border); border-radius: 12px;
}
input::placeholder { color: var(--muted); }
input:focus-visible, button:focus-visible, a:focus-visible {
  outline: 2px solid var(--text); outline-offset: 2px;
}
input[inputmode="numeric"] { letter-spacing: .18em; }

button {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 40px; padding: 0 24px;
  font: 600 14px/1 var(--font-sans); letter-spacing: .01em;
  cursor: pointer; border: none; border-radius: 999px;
  background: var(--btn-bg); color: var(--btn-fg);
  backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px);
}
button[disabled] { opacity: .45; cursor: not-allowed; }
button.danger { background: var(--btn-danger-bg); color: var(--btn-danger-fg); }
button.quiet { background: var(--btn-quiet-bg); color: var(--btn-quiet-fg); }
.actions {
  display: flex; flex-wrap: wrap; gap: .75rem;
  align-items: center; margin-top: var(--space-6);
}

.step { display: none; }
.step.active { display: block; }
.check {
  display: flex; gap: .7rem; align-items: flex-start;
  margin-top: var(--space-4); font-size: 14px; line-height: 1.5;
}
.check input { margin-top: .25rem; flex: none; accent-color: var(--danger); }
.note { color: var(--dim); font-size: 14px; }
.card.warn .note, .card.warn label { color: rgba(255, 255, 255, .72); }
.msg {
  margin-top: var(--space-4); padding: .8rem 1rem;
  border-radius: 12px; font-size: 14px; display: none;
}
.msg.show { display: block; }
.msg.error { background: color-mix(in srgb, var(--danger) 14%, transparent); color: var(--danger); }
.msg.ok { background: color-mix(in srgb, var(--success) 16%, transparent); color: var(--success); }

footer { border-top: 1px solid var(--border); margin-top: var(--space-10); }
footer .wrap {
  padding-top: var(--space-6); padding-bottom: var(--space-8);
  color: var(--muted); font-size: 14px;
}
footer p { color: inherit; margin: .3rem 0; }

/* Phones: the same page, not a smaller one. Buttons take the full width so a
   thumb has an obvious target, and the tracking on the wordmark comes in. */
@media (max-width: 30rem) {
  .wrap { padding-left: var(--space-4); padding-right: var(--space-4); }
  .brand { letter-spacing: .22em; }
  nav { gap: var(--space-4); }
  .actions { gap: var(--space-2); }
  .actions button { width: 100%; }
}
`

/** Header nav, with the current page marked for both sighted readers and screen readers. */
const navMarkup = (active: PageKey): string =>
  NAV.map(({ href, label }) => {
    const current = href === active ? ' aria-current="page"' : ''
    return `<a href="${href}"${current}>${label}</a>`
  }).join('')

export interface PageOptions {
  title: string
  /** Meta description — what a Play reviewer or a search result sees. */
  description: string
  active: PageKey
  /** Markup for everything between the header and the footer. */
  body: string
  /**
   * Extra `<head>` markup. Only the deletion page uses it, to warm up the connections to
   * Google's servers before its module scripts ask for them.
   */
  head?: string
}

/** Wrap page markup in the shared document. */
export const page = ({ title, description, active, body, head = '' }: PageOptions): string => `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} · ${IDENTITY.product}</title>
<meta name="description" content="${escapeHtml(description)}">
<meta name="robots" content="index, follow">
<link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'><rect width='32' height='32' rx='7' fill='%23FFFFFF'/><circle cx='16' cy='16' r='8' fill='%23000000'/></svg>">
${head}
<style>${STYLES}</style>
</head>
<body>
<header><div class="wrap">
  <a class="brand" href="./">${IDENTITY.product}</a>
  <nav>${navMarkup(active)}</nav>
</div></header>
<main><div class="wrap">
${body}
</div></main>
<footer><div class="wrap">
  <p>Last updated ${IDENTITY.lastUpdated}.</p>
  <p>Questions? <a href="mailto:${IDENTITY.supportEmail}">${IDENTITY.supportEmail}</a></p>
</div></footer>
</body>
</html>`

/**
 * Everything the Terms and Privacy pages are allowed to load, which is nothing.
 *
 * They have no scripts, no images and no webfonts — only the stylesheet this file inlines,
 * hence `style-src 'unsafe-inline'` and nothing else. The deletion page cannot use this and
 * passes its own; see `delete-account.ts`.
 */
export const STATIC_CSP =
  "default-src 'none'; style-src 'unsafe-inline'; img-src data:; form-action 'none'; base-uri 'none'; frame-ancestors 'none'"

export interface ResponseOptions {
  status?: number
  /** Seconds of shared-cache lifetime. Zero marks the response uncacheable. */
  cacheSeconds?: number
  csp?: string
}

/** A page response. Cached briefly so a Play review crawl does not re-render on every hit. */
export const htmlResponse = (
  markup: string,
  { status = 200, cacheSeconds = 300, csp = STATIC_CSP }: ResponseOptions = {},
): Response =>
  new Response(markup, {
    status,
    headers: {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': cacheSeconds > 0 ? `public, max-age=${cacheSeconds}` : 'no-store',
      'Content-Security-Policy': csp,
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
    },
  })
