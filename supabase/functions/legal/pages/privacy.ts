// The Privacy Policy.
//
// Written against what the app actually does, not against a template. Every row of the table
// below maps to a real column or bucket in `docs/SUPABASE_BOOTSTRAP.md`, and every permission
// listed is one that `androidApp/src/main/AndroidManifest.xml` declares. When either of those
// changes, this file is part of the change — a privacy notice that describes an older build
// is worse than a short one, because it is confidently wrong.
//
// Two claims here are load-bearing and worth re-checking before every release:
//   - Bill and document text is read on the device. True while `:infrastructure:ai` uses
//     ML Kit locally and nothing ships the image to a server for parsing.
//   - Trip coordinates never leave the phone. True while `TripDto` has no coordinate fields
//     (see `core/data/.../trip/TripRemoteDataSource.kt`).

import { IDENTITY, operatorName } from '../identity.ts'
import { page } from './layout.ts'

const contactBlock = `
<p>
  Write to <a href="mailto:${IDENTITY.grievanceEmail}">${IDENTITY.grievanceEmail}</a> for anything on
  this page — a question, a correction, a copy of your data, or a complaint. We answer within
  30 days, and usually the same week.
</p>
${IDENTITY.registeredAddress ? `<p>${IDENTITY.registeredAddress}</p>` : ''}
`

export const privacyPage = (): string =>
  page({
    title: 'Privacy Policy',
    description:
      'What Odo collects about you and your car, where it is stored, who else can see it, and how to delete it.',
    active: 'privacy',
    body: `
<h1>Privacy Policy</h1>
<p class="lede">
  ${IDENTITY.product} keeps a service history for your car. That means it holds real details about you and
  a vehicle you own, so this page says plainly what those details are, where they sit, and how
  to get rid of them.
</p>

<h2>The short version</h2>
<ul>
  <li>Your phone number is the only thing ${IDENTITY.product} requires. Everything else is optional.</li>
  <li>Photos of bills and documents are read <strong>on your phone</strong>. The text is extracted there,
      not on a server.</li>
  <li>If you turn on trip tracking, the GPS coordinates <strong>never leave your phone</strong>. Only the
      distance and the times are synced.</li>
  <li>We do not sell your data, and we do not share it with advertisers or insurers.</li>
  <li>You can delete your account and everything in it yourself, from
      <a href="delete-account">this website</a>, without installing the app.</li>
</ul>

<h2>Who this is from</h2>
<p>
  ${operatorName()} operates the ${IDENTITY.product} mobile app and this website, and decides how the
  data described below is handled. Under India's Digital Personal Data Protection Act, 2023,
  that makes us the Data Fiduciary and you the Data Principal.
</p>

<h2>What we collect</h2>
<p>
  Almost all of it is something you typed or photographed. Nothing here is bought from a third
  party or inferred from your behaviour on other apps.
</p>

<div class="scroll-x"><table>
  <thead><tr><th>What</th><th>Why</th><th>Where it lives</th></tr></thead>
  <tbody>
    <tr>
      <td><strong>Phone number</strong></td>
      <td>It is your account. We verify it by SMS so nobody else can open your service history.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Name, email, home city, profile photo</strong></td>
      <td>Optional. The city is used to price-check a bill against local rates.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Car details</strong> — registration number, make, model, variant, fuel type, model year, odometer readings</td>
      <td>The record the whole app is built around, and what makes reminders and cost-per-km possible.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Service records</strong> — dates, workshop name, amounts, categories, your notes</td>
      <td>Your service history, and the input to the health score.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Bill photos and the line items read from them</strong></td>
      <td>So a bill can be checked line by line against typical rates instead of retyped.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Vehicle documents</strong> — registration certificate, insurance, PUC, driving licence: the file and its expiry date</td>
      <td>So the app can warn you before one expires and you can produce it when asked.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Reminders, health scores, cost-per-km history</strong></td>
      <td>Calculated from the records above and kept so trends survive a reinstall.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Trips</strong> — start and end time, distance covered</td>
      <td>Only if you switch trip tracking on. Used to keep the odometer current without you typing it.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>GPS coordinates</strong></td>
      <td>Used on the device to measure how far a trip went.</td>
      <td><strong>Your phone only</strong> — never uploaded</td>
    </tr>
    <tr>
      <td><strong>Notification token</strong></td>
      <td>Lets us push a reminder to your phone. It identifies the installation, not you.</td>
      <td>Your phone and our servers</td>
    </tr>
    <tr>
      <td><strong>Diagnostic logs</strong></td>
      <td>Written on your device as the app runs. Uploaded <em>only</em> when you choose to send a problem report. Phone numbers, emails and similar identifiers are masked before the file is written.</td>
      <td>Your phone; our servers only if you send a report</td>
    </tr>
    <tr>
      <td><strong>Crash reports and usage events</strong> — screens opened, features used, app version, device model, OS version</td>
      <td>To find crashes and see which parts of the app are worth improving. Not tied to your name or number.</td>
      <td>Google Firebase</td>
    </tr>
  </tbody>
</table></div>

<h3>What we deliberately do not collect</h3>
<ul>
  <li>Your contacts, call logs, SMS messages or photo library.</li>
  <li>Your chassis or engine number.</li>
  <li>Any advertising identifier. ${IDENTITY.product} carries no ad SDK and no tracking pixel.</li>
</ul>

<h2>Permissions the app asks for</h2>
<p>
  Android asks you before granting any of these, and the app asks at the moment the feature
  needs it rather than at launch. Declining any of them leaves the rest of ${IDENTITY.product} working.
</p>
<ul>
  <li><strong>Camera</strong> — to scan a bill, a document or a UPI payment QR code. Frames are read on the
      device; nothing is uploaded unless you save the photo to a record.</li>
  <li><strong>Location, including in the background</strong> — only for trip tracking, and only after you
      switch it on. Background access is what lets a trip keep measuring while your phone is in
      your pocket. The coordinates stay on the device.</li>
  <li><strong>Physical activity</strong> — to notice that you have started driving, so a trip can begin
      without you tapping anything.</li>
  <li><strong>Nearby devices (Bluetooth)</strong> — to notice your car's stereo connecting or
      disconnecting, which is the cheapest signal that a drive has started or ended. We read the
      connection event, not the device's contents.</li>
  <li><strong>Notifications</strong> — to show reminders about an expiring document or a due service.</li>
</ul>

<h2>Who else can see it</h2>
<p>
  Only the companies that run the infrastructure underneath ${IDENTITY.product}. Each of them processes data
  on our instructions and for no purpose of their own.
</p>
<ul>
  <li><strong>Supabase</strong> — the database and file storage holding your records. Hosted in the
      Asia-Pacific region.</li>
  <li><strong>Google Firebase</strong> — sends the SMS that verifies your number, delivers reminder
      notifications, and collects crash reports and usage events.</li>
  <li><strong>Google Play</strong> — distributes the app and handles any purchase you make through it.</li>
</ul>
<p>
  We will also disclose data where the law genuinely requires it — a court order or a valid
  demand from an authority. We do not hand over service histories on informal request, and we
  will tell you if we are permitted to.
</p>
<p class="note">
  Some of these providers operate outside India. Where your data is processed abroad, it is
  under contractual terms that keep the protections described here in force.
</p>

<h2>How long we keep it</h2>
<ul>
  <li><strong>Your records</strong> — for as long as your account exists. Nothing expires on its own,
      because a five-year-old service entry is the point of a service history.</li>
  <li><strong>Deleted records</strong> — when you delete a car or a document in the app, it is hidden
      immediately and cleared from our servers within 30 days.</li>
  <li><strong>Diagnostic logs you sent us</strong> — 90 days.</li>
  <li><strong>Your whole account</strong> — erased when you ask, see below.</li>
</ul>

<h2>Deleting your data</h2>
<p>There are two things you might want to delete, and they are separate.</p>
<div class="card">
  <h3>The copy on your phone</h3>
  <p>
    Open <strong>Profile → Delete my data</strong> in the app, or uninstall ${IDENTITY.product}. Either clears the
    local database and the files it saved.
  </p>
  <h3>Your account and everything on our servers</h3>
  <p>
    Go to <a href="delete-account"><strong>${IDENTITY.product} · Delete account</strong></a>, verify your phone number
    by SMS, and confirm. Your profile, cars, service records, bills, documents, reminders,
    scores and uploaded files are erased immediately and permanently. You do not need the app
    installed to do this.
  </p>
</div>
<p>
  One thing survives, and it is not linked to you: the anonymous price points that make bill
  checking work. When a bill is checked, the amount, the service category and the city are
  added to a pool that carries no account, car or bill reference and cannot be traced back to
  anyone. That pool is what lets the next owner know an air-filter change should not cost
  ₹2,000, and it remains after deletion.
</p>

<h2>Keeping it safe</h2>
<ul>
  <li>Everything travels over HTTPS, and the database and file storage are encrypted at rest.</li>
  <li>Access rules are enforced by the database itself, per row, keyed to your account — not by
      the app asking politely.</li>
  <li>Your bills and documents sit in private buckets. Nothing is served by a public link;
      a file is reached through a signed URL that expires.</li>
  <li>Sign-in is by SMS code. There is no password to leak or reuse.</li>
</ul>
<p class="note">
  No system is perfect. If we ever discover a breach affecting your data, we will notify you
  and the Data Protection Board of India as the DPDP Act requires.
</p>

<h2>Your rights</h2>
<p>Under the DPDP Act, 2023 you can:</p>
<ul>
  <li><strong>Ask what we hold</strong> — a summary of your data and who we have shared it with.</li>
  <li><strong>Correct it</strong> — most fields are editable in the app; email us for anything that is not.</li>
  <li><strong>Erase it</strong> — the deletion page above does this immediately, no request needed.</li>
  <li><strong>Nominate someone</strong> — to exercise these rights on your behalf if you die or are
      incapacitated.</li>
  <li><strong>Complain</strong> — to us first, at the address below, and to the Data Protection Board of
      India if we do not resolve it.</li>
</ul>

<h2>Children</h2>
<p>
  ${IDENTITY.product} is for vehicle owners and is not directed at anyone under 18. We do not knowingly
  collect data from children. If you believe a child has created an account, email us and we
  will remove it.
</p>

<h2>Changes to this policy</h2>
<p>
  If we change what we collect or who we share it with, we will update this page and change the
  date in the footer. For a change that materially affects you, we will also tell you in the
  app before it takes effect.
</p>

<h2>Contact and grievances</h2>
${contactBlock}
`,
  })
