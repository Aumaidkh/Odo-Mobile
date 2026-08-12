-- Put the `+` back on the phone number handle_new_user copies into profiles.
--
-- GoTrue stores auth.users.phone as bare digits — `918082448747`. profiles.phone is checked
-- against `^\+[1-9]\d{7,14}$`, full E.164. So the trigger's straight copy of NEW.phone fails
-- the constraint, the insert aborts, and GoTrue reports the whole signup as the unhelpfully
-- generic "Database error creating new user".
--
-- Latent since the schema was written, and only reachable now. Every account so far has been
-- created by email and password, where NEW.phone is null and the `phone IS NULL` branch of the
-- constraint lets it through. The first phone signup is the first time the other branch runs.
--
-- Idempotent — CREATE OR REPLACE, and the trigger already points at this function by name.

CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    INSERT INTO profiles (id, full_name, phone)
    VALUES (
        NEW.id,
        NEW.raw_user_meta_data ->> 'full_name',
        -- ltrim first, so a `+` that is already there does not become `++`. Empty string as
        -- well as NULL, because GoTrue uses both for "no phone" depending on how the row was
        -- created, and '' would fail the constraint just as surely as unprefixed digits.
        CASE
            WHEN NEW.phone IS NULL OR NEW.phone = '' THEN NULL
            ELSE '+' || ltrim(NEW.phone, '+')
        END
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO subscriptions (owner_id, tier)
    VALUES (NEW.id, 'free')
    ON CONFLICT (owner_id) DO NOTHING;

    RETURN NEW;
END;
$$;
