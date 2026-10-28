-- Make logos for the make picker (release 1.4.5).
-- Adds a nullable logo_path to vehicle_makes: a Storage path like
-- 'vehicle-logos/makes/make-kia.png' that each app turns into a URL on its own API host.
-- Nullable and additive, so older apps (which ignore unknown columns) are unaffected.
-- The files live in the public 'vehicle-logos' bucket: anyone may read them, only the
-- service role writes. Premier has no logo and keeps its monogram.

alter table public.vehicle_makes add column if not exists logo_path text;

comment on column public.vehicle_makes.logo_path is
    'Storage path (<bucket>/<object>) of the make logo, or null to show the monogram.';

insert into storage.buckets (id, name, public)
values ('vehicle-logos', 'vehicle-logos', true)
on conflict (id) do update set public = true;

update public.vehicle_makes
set logo_path = 'vehicle-logos/makes/' || id || '.png'
where id in (
    'make-maruti-suzuki', 'make-hyundai', 'make-tata', 'make-mahindra', 'make-toyota',
    'make-kia', 'make-honda', 'make-renault', 'make-volkswagen', 'make-skoda', 'make-mg',
    'make-nissan', 'make-jeep', 'make-citroen', 'make-byd', 'make-mercedes-benz', 'make-bmw',
    'make-audi', 'make-volvo', 'make-land-rover', 'make-jaguar', 'make-lexus', 'make-mini',
    'make-porsche', 'make-isuzu', 'make-force-motors', 'make-mitsubishi', 'make-fiat',
    'make-chevrolet', 'make-datsun'
);
