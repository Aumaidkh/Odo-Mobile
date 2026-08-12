// Who these pages are published by.
//
// One place, because the same four facts appear in the Terms, the Privacy Policy and the
// account-deletion page, and a legal notice that contradicts itself on who is answering is
// worse than one that says less.
//
// `registeredAddress` and `legalEntity` are the two that cannot be guessed from the codebase.
// Both are optional here and the templates omit the block when they are null, so an
// unfilled deploy reads as a product notice rather than a broken one — but a published
// privacy notice under the DPDP Act 2023 is expected to name the entity and a grievance
// contact, so fill them before the app is public.

export const IDENTITY = {
  /** Product name, as it appears in the app and on the store listing. */
  product: 'Odo',

  /**
   * The company that operates Odo. Null until the registered name is confirmed — the
   * templates then say "Odo" alone rather than printing a placeholder.
   */
  legalEntity: null as string | null,

  /** Registered office. Null until confirmed; the contact block omits the line. */
  registeredAddress: null as string | null,

  /** Support inbox. Same address the in-app Help & support sheet shows. */
  supportEmail: 'support.odo.in@gmail.com',

  /**
   * Where privacy complaints go. The DPDP Act requires a published route for grievances;
   * routing it to the support inbox is fine while there is one person reading both.
   */
  grievanceEmail: 'support.odo.in@gmail.com',

  /**
   * The date these documents last changed, shown in the footer of every page.
   *
   * Hand-maintained on purpose. A build timestamp would make the footer move on every
   * unrelated redeploy, which is exactly the signal a reader uses to decide whether the
   * terms they agreed to still apply.
   */
  lastUpdated: '11 August 2026',
} as const

/** "Odo Technologies Pvt Ltd ("Odo")" when the entity is known, otherwise just "Odo". */
export const operatorName = (): string =>
  IDENTITY.legalEntity ? `${IDENTITY.legalEntity} ("${IDENTITY.product}")` : IDENTITY.product
