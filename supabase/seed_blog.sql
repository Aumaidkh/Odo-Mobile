-- Enough content to see the blog working, once.
--
-- Not part of the migrations: a migration runs on every environment forever, and
-- this is a one-off so that a freshly created project is not a black page with a
-- nav on it. Run it by hand, once, and never again:
--
--   psql "$DATABASE_URL" -f supabase/seed_blog.sql
--
-- The posts are the ones the design was drawn with, so what appears is what the
-- frames show. Every insert is idempotent — re-running replaces the rows rather
-- than duplicating them — because the most likely second run is somebody who is
-- not sure whether the first one worked.
--
-- The author row is left alone on conflict. `blog-session` creates one on first
-- sign-in from the address in BLOG_AUTHOR_EMAILS, and that row is the real one;
-- this only fills in a name and a bio so a byline has something to draw.

insert into public.blog_categories (slug, name, blurb, position) values
  ('challans',      'Challans',      'E-challans, court cases and payment',        1),
  ('service-costs', 'Service costs', 'Rates taken from real bills',                2),
  ('documents',     'Documents',     'RC, insurance, PUC and renewals',            3),
  ('resale',        'Resale',        'Before you sell, and while you are selling', 4),
  ('fuel',          'Fuel',          'Mileage, running cost and fuel logging',     5)
on conflict (slug) do update
set name = excluded.name, blurb = excluded.blurb, position = excluded.position;

-- A placeholder author, so the seeded posts have a byline. Replace the email with
-- the one in BLOG_AUTHOR_EMAILS to have your own account own these posts.
insert into public.blog_authors (slug, email, name, initial, bio, topics, since_label) values
  (
    'odo',
    'hello@odoapp.in',
    'Odo',
    'O',
    'We scan every bill for our own cars, and write down what we learn.',
    'Challans · Costs · Resale',
    'March 2025'
  )
on conflict (email) do nothing;

-- One post written out in full — the one the design draws at every size, with a
-- section, a bolded run, a callout and the app shown after the answer is already
-- complete. The rest carry their dek as the body, which is enough to prove the
-- routing and the cards without turning a seed file into a writing project.
insert into public.blog_posts
  (slug, title, dek, category_slug, author_id, status, published_on, reading_minutes, word_count, seo_title, meta_description, body)
select
  'how-to-check-challans',
  'How to check your challans — the full guide',
  'Parivahan, state portals and apps — every route, and what each one misses.',
  'challans',
  a.id,
  'published',
  current_date - 2,
  8,
  312,
  'How to check your challans — the full guide',
  'Parivahan, state portals and apps — every route, and what each one misses.',
  $json$[
    {"type":"paragraph","runs":[{"text":"Most people never find out that there is a challan in their name. A challan issued by a camera does not arrive at your door — it goes straight into a database, and you hear about it when you go to renew your insurance or sell the car.","bold":false}]},
    {"type":"section","id":"what-a-challan-is","text":"What a challan is"},
    {"type":"paragraph","runs":[
      {"text":"There are two kinds. ","bold":false},
      {"text":"On the spot","bold":true},
      {"text":" — the traffic police stopped you and handed over a receipt. And an ","bold":false},
      {"text":"e-challan","bold":true},
      {"text":" — a camera caught it, nobody stopped you, and the notice only exists in the system.","bold":false}
    ]},
    {"type":"callout","label":"WORTH KNOWING","runs":[{"text":"An e-challan left unpaid for 90 days goes to court in several states. After that it cannot be paid online — you have to appear in person.","bold":false}]},
    {"type":"section","id":"checking-on-parivahan","text":"Checking on Parivahan"},
    {"type":"paragraph","runs":[{"text":"Open echallan.parivahan.gov.in, enter the registration number and the last five digits of the chassis number, then verify the OTP. This is the central database — but not every state feeds it at the same speed.","bold":false}]},
    {"type":"section","id":"checking-in-odo","text":"Checking in Odo — one place"},
    {"type":"showcase","heading":"Odo has a screen for this","body":"Enter the number and Odo checks every source at once, shows the whole list, and tells you how old the data behind it is.","cta":"Download Odo"},
    {"type":"section","id":"how-long-you-have","text":"How long you have"},
    {"type":"paragraph","runs":[{"text":"As little time as you can take. Several states add a late fee after 60 days, and at 90 days it becomes a court reference — at which point the amount rises and the process gets long.","bold":false}]}
  ]$json$::jsonb
from public.blog_authors a
where a.email = 'hello@odoapp.in'
on conflict (slug) do update
set title = excluded.title, dek = excluded.dek, body = excluded.body, status = excluded.status;

insert into public.blog_posts
  (slug, title, dek, category_slug, author_id, status, published_on, reading_minutes, seo_title, meta_description, body)
select p.slug, p.title, p.dek, p.category_slug, a.id, 'published', p.published_on, p.reading_minutes, p.title, p.dek,
  jsonb_build_array(jsonb_build_object('type', 'paragraph', 'runs',
    jsonb_build_array(jsonb_build_object('text', p.dek, 'bold', false))))
from (values
  ('new-tyre-prices',               'What new tyres should actually cost',            'By brand, size and city — from real bills.',                     'service-costs', current_date - 6,  5),
  ('brake-pad-prices',              'What should brake pads cost?',                   'Real rates from Pune, Delhi and Mumbai — from 240 verified bills.', 'service-costs', current_date - 9,  5),
  ('challans-that-go-to-court',     'When a challan goes to court',                   'What changes after 90 days, and what you have to do about it.',  'challans',      current_date - 11, 5),
  ('expired-puc',                   'Your PUC expired — now what?',                   'What the fine is, how to renew, and how long it takes.',         'documents',     current_date - 16, 4),
  ('disputing-a-wrong-challan',     'A challan that is not yours — how to dispute it', 'From a number-plate mismatch to a wrong location.',              'challans',      current_date - 18, 6),
  ('five-things-before-selling',    'Five things to do before you sell',              'Small jobs that look large to a buyer.',                         'resale',        current_date - 23, 6),
  ('losing-mileage',                'Losing mileage?',                                'The most common reason is tyre pressure — and that one is free.', 'fuel',          current_date - 30, 4),
  ('when-to-get-a-wheel-alignment', 'When to get a wheel alignment',                  'The signs that say it is time.',                                 'service-costs', current_date - 44, 4)
) as p(slug, title, dek, category_slug, published_on, reading_minutes)
cross join public.blog_authors a
where a.email = 'hello@odoapp.in'
on conflict (slug) do update
set title = excluded.title, dek = excluded.dek, status = excluded.status;
