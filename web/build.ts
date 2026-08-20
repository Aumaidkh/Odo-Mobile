// Renders the legal pages to static HTML for Firebase Hosting.
//
// They exist because Supabase rewrites any `text/html` response on its default domain to
// `text/plain` and forces a `default-src 'none'; sandbox` policy over it. The pages arrived
// intact but unrendered, and the deletion page's script could not run at all. Firebase Hosting
// serves them as HTML, applies the policy in `firebase.json`, and — because the deletion page
// is a Firebase phone-auth client — its `.web.app` domain is already an authorized domain.
//
// The page functions stay in `supabase/functions/legal/` and are imported from here, so there
// is one copy of the markup. The edge function still serves the same pages; what it serves is
// just no longer what anybody reads.
//
// Run: deno run --allow-read --allow-write --allow-env web/build.ts [dev|prod]
//
// Or `sh web/deploy.sh <dev|prod>`, which builds and deploys in one step so the pages and the
// project they are deployed to cannot disagree.

import { deleteAccountPage } from '../supabase/functions/legal/pages/delete-account.ts'
import { indexPage, notFoundPage } from '../supabase/functions/legal/pages/index-page.ts'
import { privacyPage } from '../supabase/functions/legal/pages/privacy.ts'
import { termsPage } from '../supabase/functions/legal/pages/terms.ts'

/** One deployment target: a Firebase project to host and sign in against, a Supabase to erase in. */
interface LegalEnvironment {
  /** Hosting site and Firebase Auth tenant. Also the `.web.app` host the pages are read from. */
  readonly firebaseProjectId: string
  /**
   * The Firebase web config the deletion page initialises with.
   *
   * Public client identifiers only — the same class of value the app ships in
   * `google-services.json` — so baking them into a static file gives away nothing.
   */
  readonly webConfig: Record<string, string>
  /** Supabase project ref. Where the deletion page POSTs its ID token; the function erases. */
  readonly supabaseRef: string
}

/**
 * The two environments, matched to the app's build types.
 *
 * Debug and stage builds sign in against `odo-mobile-dev` and write to the dev Supabase project
 * (`infrastructure/supabase/build.gradle.kts` decides that from the build type), so the pages a
 * dev build links to have to be the pair that can actually delete what it created. Release gets
 * the production pair, and that is the one the Play listing points at.
 */
const ENVIRONMENTS: Record<string, LegalEnvironment> = {
  prod: {
    firebaseProjectId: 'odo-mobile-ba9aa',
    webConfig: {
      apiKey: 'AIzaSyB8A39cTEw-_4mtRntVatyf5ZWYhiwojUc',
      authDomain: 'odo-mobile-ba9aa.firebaseapp.com',
      projectId: 'odo-mobile-ba9aa',
      appId: '1:473912645018:web:a54e83d832793397bc1623',
    },
    supabaseRef: 'kxxgfhwnidgfvjowqaad',
  },
  dev: {
    firebaseProjectId: 'odo-mobile-dev',
    webConfig: {
      apiKey: 'AIzaSyCjp5SzC8s8sFOTA5_aak3v-0psjDJ-eX8',
      authDomain: 'odo-mobile-dev.firebaseapp.com',
      projectId: 'odo-mobile-dev',
      appId: '1:654451333602:web:b59417508fca317426c1bb',
    },
    supabaseRef: 'gezicmstbgfpwwohiboq',
  },
}

// Production unless asked for otherwise, the same default the Gradle side takes for an
// unrecognised build: the wrong guess should point at the pages that already exist publicly.
const NAME = (Deno.args[0] ?? Deno.env.get('LEGAL_ENV') ?? 'prod').trim().toLowerCase()
const ENVIRONMENT = ENVIRONMENTS[NAME]
if (ENVIRONMENT === undefined) {
  console.error(`unknown environment '${NAME}' — expected one of ${Object.keys(ENVIRONMENTS).join(', ')}`)
  Deno.exit(1)
}

/** Where the deletion page POSTs its ID token. The function keeps doing the erasing. */
const ERASE_ENDPOINT = Deno.env.get('LEGAL_ERASE_ENDPOINT') ??
  `https://${ENVIRONMENT.supabaseRef}.supabase.co/functions/v1/legal/delete-account`

// Both stay overridable, for a custom domain or a project not in the table above.
const WEB_CONFIG = Deno.env.get('FIREBASE_WEB_CONFIG') ?? JSON.stringify(ENVIRONMENT.webConfig)

const OUT = new URL('./public/', import.meta.url)

/**
 * The marketing site's copy, served at odoapp.in/legal/.
 *
 * The two sites are separate Firebase Hosting sites with separate configs, so a page reachable
 * on one is not reachable on the other; the documents have to exist in both trees. They are
 * written from the same markup in the same run, which is the only way the two copies cannot
 * disagree about what the policy says.
 */
const LANDING_OUT = new URL('../landing/public/legal/', import.meta.url)

/**
 * Every page, not only the two that were asked for.
 *
 * Each one's nav links to the others, so shipping a subset would mean shipping a privacy page
 * whose own header 404s.
 */
const PAGES: Record<string, string> = {
  'index.html': indexPage(),
  'terms.html': termsPage(),
  'privacy.html': privacyPage(),
  'delete-account.html': deleteAccountPage(WEB_CONFIG, ERASE_ENDPOINT),
  '404.html': notFoundPage(),
}

await Deno.mkdir(OUT, { recursive: true })
for (const [name, markup] of Object.entries(PAGES)) {
  await Deno.writeTextFile(new URL(name, OUT), markup)
  console.log(`${name}  ${markup.length} bytes`)
}

// Only production is copied to the marketing site. A dev build writing there would leave a page
// wired to the scratch project sitting on the public domain, and nothing in the HTML says which
// environment it came from — the same trap `web/public` already carries, but on a page anyone
// can reach from the store listing.
//
// The deletion page is written as `delete.html` because odoapp.in/legal/delete is the path we
// publish. The pages' own nav links relatively to `delete-account`, so `landing/firebase.json`
// redirects `/legal/delete-account` onto it; without that redirect the header on the privacy
// page 404s.
if (NAME === 'prod') {
  const LANDING_PAGES: Record<string, string> = {
    'terms.html': PAGES['terms.html'],
    'privacy.html': PAGES['privacy.html'],
    'delete.html': PAGES['delete-account.html'],
  }
  await Deno.mkdir(LANDING_OUT, { recursive: true })
  for (const [name, markup] of Object.entries(LANDING_PAGES)) {
    await Deno.writeTextFile(new URL(name, LANDING_OUT), markup)
    console.log(`legal/${name}  ${markup.length} bytes`)
  }
}

// `web/public` holds whichever environment was built last and nothing records which. Say so,
// because deploying the wrong pair points a production page at a scratch database.
console.log(`\n${NAME}: firebase ${ENVIRONMENT.firebaseProjectId}, erase ${ERASE_ENDPOINT}`)
