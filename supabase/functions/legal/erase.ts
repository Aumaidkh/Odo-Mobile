// Erasing one owner, everything they stored, and the account row itself.
//
// The database does nearly all of it on its own. Every table hangs off `profiles`, `profiles`
// hangs off `auth.users` with ON DELETE CASCADE, so one `deleteUser` takes cars, service logs,
// bills, line items, documents, reminders, health scores, per-km snapshots, device tokens,
// overcharge reports and subscriptions with it. That is deliberate — an erasure routine that
// lists tables by hand goes stale the first time somebody adds one, and goes stale silently.
//
// Two things the cascade cannot reach, and this file exists for them:
//
//   1. Storage. Objects in the buckets are not foreign-keyed to anything; the owner id is just
//      the first path segment. They have to be listed and removed through the Storage API so
//      the underlying files go too, not only their rows.
//   2. The two tables that reference `profiles` with ON DELETE RESTRICT rather than CASCADE —
//      `payments` and `resale_passports`. RESTRICT is there so a financial record cannot
//      vanish by accident, which means it also blocks a deliberate deletion until the rows are
//      cleared first.
//
// Order matters: storage first (so a half-finished run leaves files that are still reachable
// through a live account rather than orphaned files nobody can see), then the restricted
// tables, then the user.

import { createClient, type SupabaseClient } from 'jsr:@supabase/supabase-js@2'

/**
 * Every bucket keyed on `{owner_id}/…` — see §19 of `docs/SUPABASE_BOOTSTRAP.md`.
 *
 * A bucket missing from this list means its files survive the account that owned them, so
 * adding a bucket there means adding it here.
 */
const OWNER_BUCKETS = ['bill-photos', 'documents', 'passports', 'avatars', 'app-logs'] as const

/**
 * Tables whose foreign key to `profiles` is ON DELETE RESTRICT, in the order they must be
 * cleared: `payments.resale_passport_id` points at `resale_passports`, so payments go first.
 *
 * Both belong to features that are not switched on yet, so in practice these delete nothing
 * today. They are here so the day one of them holds a row is not the day deletion starts
 * failing with a foreign-key error nobody can read.
 */
const RESTRICTED_TABLES = ['payments', 'resale_passports'] as const

/** Storage `list` caps out at 100 per call by default; ask for the maximum it allows. */
const LIST_PAGE_SIZE = 1000

/** `remove` takes an array of paths. Chunked so one bucket with many files is still one pass. */
const REMOVE_CHUNK_SIZE = 100

/** Postgres/PostgREST codes meaning "that table is not in this database", which is fine. */
const MISSING_TABLE_CODES = new Set(['42P01', 'PGRST205'])

export interface EraseResult {
  /** How many stored files were removed, across every bucket. */
  filesRemoved: number
}

/** A service-role client. Bypasses row-level security, which erasing another user requires. */
export const adminClient = (url: string, serviceRoleKey: string): SupabaseClient =>
  createClient(url, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

/**
 * List every object under `prefix` in `bucket`, walking into folders.
 *
 * The Storage API lists one level at a time and marks a folder by returning it with a null
 * `id`. Paths here are `{owner}/{car}/{record}.{ext}`, so this recurses twice in practice, but
 * nothing depends on that depth.
 */
const listObjects = async (
  admin: SupabaseClient,
  bucket: string,
  prefix: string,
): Promise<string[]> => {
  const found: string[] = []
  let offset = 0

  while (true) {
    const { data, error } = await admin.storage
      .from(bucket)
      .list(prefix, { limit: LIST_PAGE_SIZE, offset })

    // A bucket that does not exist in this project, or one this key cannot see. Neither is a
    // reason to abandon the deletion — the other buckets still need clearing.
    if (error) {
      console.error(`erase: listing ${bucket}/${prefix} failed:`, error.message)
      return found
    }
    if (!data || data.length === 0) return found

    for (const entry of data) {
      const path = `${prefix}/${entry.name}`
      if (entry.id === null) found.push(...(await listObjects(admin, bucket, path)))
      else found.push(path)
    }

    if (data.length < LIST_PAGE_SIZE) return found
    offset += data.length
  }
}

/** Remove every file the owner has in one bucket. Returns how many went. */
const emptyBucket = async (
  admin: SupabaseClient,
  bucket: string,
  ownerId: string,
): Promise<number> => {
  const paths = await listObjects(admin, bucket, ownerId)
  if (paths.length === 0) return 0

  let removed = 0
  for (let i = 0; i < paths.length; i += REMOVE_CHUNK_SIZE) {
    const chunk = paths.slice(i, i + REMOVE_CHUNK_SIZE)
    const { error } = await admin.storage.from(bucket).remove(chunk)
    // Throwing here rather than logging on: leaving a bill photo behind while telling the
    // owner everything is gone is the one outcome this whole flow exists to prevent.
    if (error) throw new Error(`removing ${chunk.length} objects from ${bucket}: ${error.message}`)
    removed += chunk.length
  }
  return removed
}

/**
 * Delete an owner's account and every trace of it.
 *
 * Throws if any step fails. The caller reports a single opaque failure to the browser and puts
 * the detail in the function log — a half-erased account must never be reported as erased.
 */
export const eraseOwner = async (admin: SupabaseClient, ownerId: string): Promise<EraseResult> => {
  let filesRemoved = 0
  for (const bucket of OWNER_BUCKETS) {
    filesRemoved += await emptyBucket(admin, bucket, ownerId)
  }

  for (const table of RESTRICTED_TABLES) {
    const { error } = await admin.from(table).delete().eq('owner_id', ownerId)
    if (error && !MISSING_TABLE_CODES.has(error.code ?? '')) {
      throw new Error(`clearing ${table}: ${error.message}`)
    }
  }

  // Hard delete, not GoTrue's soft delete: a soft-deleted row keeps the phone number, and the
  // number is the identifier the owner asked us to stop holding. This is also what fires the
  // cascade through `profiles` into every other table.
  const { error } = await admin.auth.admin.deleteUser(ownerId)
  if (error) throw new Error(`deleting auth user: ${error.message}`)

  return { filesRemoved }
}
