-- Owner-scoped pull indexes for the two tables that did not have one.
--
-- The delta pull for every car-scoped entity is now `owner_id = ? AND updated_at > cursor`
-- rather than `car_id = ?` (issue #312): a car id is not knowable at the moment a pull runs,
-- because the cars themselves may only have arrived seconds earlier in the same run.
--
-- `service_logs`, `documents`, `trips`, `fuel_fills` and `cars` already index `owner_id`.
-- `reminders` and `health_scores` only ever indexed `car_id`, so their pull would seq-scan.
-- Row-level security evaluates `owner_id = auth.uid()` on every row either query touches, so
-- these indexes pay for the policy as much as for the pull.
--
-- Both are plain, unpartial indexes. The partial `WHERE deleted_at IS NULL` form used
-- elsewhere is wrong here: a pull has to see tombstones, because a soft-deleted row is the
-- only way this device learns something was deleted on another one (SYNC_DESIGN §6).
-- `reminders` has no `deleted_at` column at all.

CREATE INDEX IF NOT EXISTS idx_reminders_owner
    ON public.reminders (owner_id);

CREATE INDEX IF NOT EXISTS idx_health_scores_owner
    ON public.health_scores (owner_id);
