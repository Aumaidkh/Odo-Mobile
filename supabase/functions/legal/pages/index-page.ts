// The two pages that are not documents: the landing page listing the other three, and the
// not-found page.
//
// They sit here rather than in `../index.ts` so the static build can import them without also
// importing that module's `Deno.serve` call, which would start a server during the build.

import { IDENTITY } from '../identity.ts'
import { page } from './layout.ts'

export const indexPage = (): string =>
  page({
    title: 'Legal',
    description: `${IDENTITY.product}'s Terms of Use, Privacy Policy, and account deletion.`,
    active: 'index',
    body: `
<h1>Legal</h1>
<p class="lede">Three pages. The third one does something.</p>
<div class="card">
  <h3 style="margin-top:0"><a href="terms">Terms of Use</a></h3>
  <p class="note">What the app does, what it does not promise, and what each side is responsible for.</p>
  <h3><a href="privacy">Privacy Policy</a></h3>
  <p class="note">What we collect about you and your car, where it is stored, and who else can see it.</p>
  <h3><a href="delete-account">Delete your account</a></h3>
  <p class="note">Verify your phone number and erase everything. No app install needed.</p>
</div>`,
  })

export const notFoundPage = (): string =>
  page({
    title: 'Not found',
    description: 'That page does not exist.',
    active: 'index',
    body: `
<h1>Not found</h1>
<p class="lede">There is no page at that address. The three that exist are in the nav above.</p>`,
  })
