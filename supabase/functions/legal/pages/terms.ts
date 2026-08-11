// The Terms of Use.
//
// The section that matters most is "What Odo does not promise". Odo tells owners when their
// insurance expires and whether a workshop bill looks high — two things a person can be
// harmed by relying on. Saying clearly that a reminder is a convenience and an estimate is an
// estimate is not lawyer padding here; it is the honest description of the feature.

import { IDENTITY, operatorName } from '../identity.ts'
import { page } from './layout.ts'

export const termsPage = (): string =>
  page({
    title: 'Terms of Use',
    description: `The agreement between you and ${IDENTITY.product}: what the app does, what it does not promise, and what each side is responsible for.`,
    active: 'terms',
    body: `
<h1>Terms of Use</h1>
<p class="lede">
  These terms are the agreement between you and ${operatorName()} for the ${IDENTITY.product} app and this
  website. Installing or using ${IDENTITY.product} means you accept them. If you do not, do not use the app.
</p>

<h2>Who can use ${IDENTITY.product}</h2>
<ul>
  <li>You must be 18 or older and able to enter a contract under Indian law.</li>
  <li>Your account is tied to your phone number, and it is yours alone. Anyone holding your
      number can receive the sign-in code, so treat the number and the device as you would a
      password.</li>
  <li>Tell us at <a href="mailto:${IDENTITY.supportEmail}">${IDENTITY.supportEmail}</a> if your number changes hands
      or you lose access to it, so the account does not follow the number to its next owner.</li>
</ul>

<h2>What ${IDENTITY.product} is</h2>
<p>
  A record-keeper and a second opinion. It stores your car's service history, reminds you
  before a document expires, reads a workshop bill and tells you whether the amounts look
  normal, and works out what the car is costing you per kilometre.
</p>

<h2>What ${IDENTITY.product} does not promise</h2>
<div class="card warn">
  <p>
    Read this section properly. It describes real limits of the features, not hypothetical ones.
  </p>
  <ul>
    <li>
      <strong>Reminders are a convenience, not a guarantee.</strong> A notification can be delayed or
      swallowed by the operating system, blocked by battery settings, or lost with the app's
      data. Keeping your insurance, PUC certificate, registration and licence valid remains
      entirely your responsibility, and we are not answerable for a fine, a claim rejection or
      any other loss caused by a reminder that did not arrive.
    </li>
    <li>
      <strong>Bill checks are estimates.</strong> The typical prices ${IDENTITY.product} compares against come from
      other owners' bills and public data. A price outside that range is not proof of
      overcharging, and a price inside it is not proof of a fair deal. Genuine parts, dealer
      workshops and city differences all move the number. Use it as a reason to ask a question,
      not as a verdict.
    </li>
    <li>
      <strong>Scanning can misread.</strong> Text is extracted from a photo automatically, and a poor
      photo, a handwritten bill or an unusual layout will produce wrong amounts or dates.
      Check what the app fills in before you save it.
    </li>
    <li>
      <strong>The health score is our opinion.</strong> It is calculated from what you have recorded, so a
      car with no records scores low even if it is perfectly maintained. It is not a mechanical
      inspection, not a valuation, and not something to buy or sell a car on.
    </li>
    <li>
      <strong>Cost per kilometre is an approximation.</strong> It relies on odometer readings you enter
      and on assumed fuel prices and mileage. Treat it as a trend, not as accounting.
    </li>
    <li>
      <strong>Odometer tracking is not a legal odometer.</strong> Distances measured from GPS or estimated
      between readings will drift from the one in your dashboard.
    </li>
  </ul>
</div>

<h2>Your data and your content</h2>
<ul>
  <li>What you put into ${IDENTITY.product} stays yours. We do not claim ownership of your records, photos or
      documents.</li>
  <li>You give us permission to store and process them only so far as running the app requires —
      syncing them to your other devices, generating reminders and scores, and showing them back
      to you.</li>
  <li>You are responsible for having the right to upload what you upload, and for not putting
      someone else's documents into your account.</li>
  <li>Keep your own copies of anything that matters. ${IDENTITY.product} is a convenient place for your
      registration certificate and insurance; it is not the only place they should exist.</li>
  <li><a href="privacy">The Privacy Policy</a> explains what we collect and how to delete it, and is part
      of these terms.</li>
</ul>

<h2>How not to use it</h2>
<ul>
  <li>Do not enter details of a vehicle you do not own or are not authorised to manage.</li>
  <li>Do not submit invented bills or prices. It corrupts the comparison data every other owner
      relies on.</li>
  <li>Do not attempt to break, overload or reverse-engineer the service, or reach data that is not
      yours.</li>
  <li>Do not resell ${IDENTITY.product} or scrape it into another product.</li>
</ul>

<h2>Paid features</h2>
<p>
  Parts of ${IDENTITY.product} may be offered as a paid subscription. Where they are, the price, the billing
  period and what is included are shown before you pay.
</p>
<ul>
  <li>Purchases made through Google Play are billed by Google, renew automatically until
      cancelled, and are managed and cancelled in your Play Store account.</li>
  <li>Refunds follow Google Play's policy. Write to us if something was charged in error and we
      will help sort it out.</li>
  <li>If a paid feature is withdrawn during a period you have paid for, you get a pro-rata refund
      for what is left of it.</li>
  <li>Cancelling stops future billing. It does not delete your account — do that from the
      <a href="delete-account">deletion page</a>.</li>
</ul>

<h2>Payments to workshops</h2>
<p>
  When ${IDENTITY.product} reads a UPI QR code from a bill, it hands the payment details to whichever UPI app
  you choose. The payment happens there, between you, your bank and the workshop. We do not
  process it, hold the money, or take a cut, and a dispute about the work or the amount is
  between you and the workshop.
</p>

<h2>Availability</h2>
<p>
  We try to keep ${IDENTITY.product} running, but we do not promise it will be available without
  interruption. Features may change or be withdrawn, and we may need to take the service down
  for maintenance. Your records stay on your phone and keep working offline while the servers
  do not.
</p>

<h2>Ending it</h2>
<ul>
  <li>You can stop at any time: delete your account from the <a href="delete-account">deletion page</a>,
      or simply uninstall.</li>
  <li>We may suspend or close an account that breaks these terms, is being used fraudulently, or
      is putting the service or other owners at risk. Except where the breach is serious, we will
      warn you first and give you a chance to export your data.</li>
</ul>

<h2>Liability</h2>
<p>
  ${IDENTITY.product} is provided as it is. To the extent Indian law allows, we exclude implied warranties, and
  we are not liable for indirect or consequential loss — lost profit, lost data, or a decision
  you took on the strength of something the app showed you.
</p>
<p>
  Where liability cannot be excluded, ours is limited to the greater of the amount you paid us
  in the twelve months before the claim, or ₹1,000. Nothing here limits liability for fraud,
  for death or personal injury caused by our negligence, or for anything else the law does not
  permit us to limit.
</p>

<h2>Changes to these terms</h2>
<p>
  We may update these terms as the app changes. The date in the footer says when they last
  changed, and we will tell you in the app before a material change takes effect. Continuing to
  use ${IDENTITY.product} after that means you accept the new version.
</p>

<h2>Governing law</h2>
<p>
  These terms are governed by the laws of India, and the courts of competent jurisdiction in
  India will hear any dispute arising from them. Please write to us first — nearly everything
  is faster to fix by email than by filing.
</p>

<h2>Contact</h2>
<p>
  <a href="mailto:${IDENTITY.supportEmail}">${IDENTITY.supportEmail}</a>
  ${IDENTITY.registeredAddress ? `<br>${IDENTITY.registeredAddress}` : ''}
</p>
`,
  })
