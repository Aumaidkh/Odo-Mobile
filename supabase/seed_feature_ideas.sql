-- The ideas list the "Suggest an idea" screen shows.
--
-- Seed data, not a migration: the catalogue is curated by whoever runs the panel, and what is
-- worth voting on changes. This is enough to make the screen real on a fresh environment —
-- without a row the section is left out entirely, which reads as a broken list rather than an
-- empty one.
--
-- `votes` is deliberately not set. The trigger keeps it, and a hand-written count would be a
-- number nobody voted for.
insert into public.feature_ideas (title, status) values
  ('Two cars on one account', 'IN_PROGRESS'),
  ('Export costs to Excel', 'UNDER_REVIEW'),
  ('Hindi interface', 'UNDER_REVIEW'),
  ('Insurance renewal reminders', 'SHIPPING')
on conflict do nothing;
