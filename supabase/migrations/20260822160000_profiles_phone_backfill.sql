-- Fill in profiles.phone for accounts that already exist without one.
--
-- profiles.phone has exactly one writer, handle_new_user(), and it is an AFTER INSERT
-- trigger on auth.users. GoTrue's admin.createUser sets the phone on the row after that
-- insert, so the trigger never sees it and the column stays NULL. Nothing on the server ever
-- revisits it: there is no AFTER UPDATE trigger and no other statement anywhere that writes
-- the column. Support, the paywall and WhatsApp reminders all need to find an owner by their
-- number, and for these accounts none of them can.
--
-- The client now sends the number on every sign-in, which repairs an account the next time
-- its owner signs in on a build that has the fix. This is for the ones already out there —
-- it repairs them now, and does not wait for an app update to reach anybody.
--
-- Only NULLs are touched. A number already on a profile is left exactly as it is, whoever
-- wrote it, so running this twice changes nothing the second time.
--
-- The regex is the same one chk_profiles_phone enforces, applied as a filter rather than
-- trusted. auth.users.phone is bare digits (`918082448747`) and the constraint wants full
-- E.164, so the `+` is put back the way handle_new_user does it; the filter is what stops a
-- single unexpected value in auth.users from aborting the whole backfill.

UPDATE public.profiles p
   SET phone = '+' || ltrim(u.phone, '+')
  FROM auth.users u
 WHERE u.id = p.id
   AND p.phone IS NULL
   AND u.phone IS NOT NULL
   AND u.phone <> ''
   AND ('+' || ltrim(u.phone, '+')) ~ '^\+[1-9]\d{7,14}$';
