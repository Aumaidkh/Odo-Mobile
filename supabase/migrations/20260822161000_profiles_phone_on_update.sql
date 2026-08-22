-- Keep profiles.phone in step when GoTrue sets or changes auth.users.phone.
--
-- handle_new_user() is an AFTER INSERT trigger, and GoTrue's admin.createUser sets the phone
-- on the row *after* that insert. So the trigger reads NULL, writes NULL, and nothing ever
-- looks again — there is no AFTER UPDATE trigger on auth.users and no other writer for the
-- column anywhere in the schema. Every phone signup lands this way.
--
-- 20260822160000 repairs the accounts that already exist. This is what stops new ones
-- arriving broken, including for owners still on a build that predates the client-side fix.
--
-- `AFTER UPDATE OF phone` fires whenever the column is named in the UPDATE, changed or not,
-- so the `IS DISTINCT FROM` keeps a rewrite of the same number from touching profiles at all
-- — no pointless row version, and no `updated_at` bump for other devices to pull.
--
-- The `+` is put back the way handle_new_user does it: GoTrue stores bare digits
-- (`918082448747`) and chk_profiles_phone wants full E.164.

CREATE OR REPLACE FUNCTION sync_profile_phone()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    e164 text := '+' || ltrim(NEW.phone, '+');
BEGIN
    -- The regex is chk_profiles_phone's own, checked here rather than relied on. A value
    -- that would fail the constraint must not reach the UPDATE: this runs inside GoTrue's
    -- transaction, so an aborted statement aborts the sign-up or sign-in that triggered it.
    -- That exact failure is what 20260810130000 was written to fix, reported to the owner as
    -- "Database error creating new user" — a NULL phone is a far smaller problem than an
    -- account that cannot be created at all.
    IF NEW.phone IS NOT NULL AND NEW.phone <> '' AND e164 ~ '^\+[1-9]\d{7,14}$' THEN
        UPDATE profiles
           SET phone = e164
         WHERE id = NEW.id
           AND phone IS DISTINCT FROM e164;
    END IF;

    RETURN NEW;
EXCEPTION
    -- Belt and braces for the same reason: nothing this trigger does is worth failing an
    -- authentication over. If the write is refused for a reason the guard above did not
    -- anticipate, the number stays NULL and the backfill or the next client sign-in picks it
    -- up. Signing in still works.
    WHEN OTHERS THEN
        RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auth_user_phone_changed ON auth.users;
CREATE TRIGGER trg_auth_user_phone_changed
    AFTER UPDATE OF phone ON auth.users
    FOR EACH ROW EXECUTE FUNCTION sync_profile_phone();
