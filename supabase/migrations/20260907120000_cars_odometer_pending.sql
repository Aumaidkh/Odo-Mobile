-- A car whose owner has not read the odometer yet.
--
-- Setup used to refuse to finish without a reading, which stopped owners who were not near
-- their car. They can skip it now; the car is stored reading zero with this flag set, and
-- the app asks again on Home and at every feature that cannot work without the number.
--
-- Zero on its own could not carry that meaning: a genuinely new car reads near zero, and
-- per-km cost, the health score and the value estimate would all take it literally.
--
-- Synced rather than kept on the device. Without it, a car restored onto a new phone would
-- arrive reading a confident zero, which is the false precision this flag exists to prevent.
--
-- Existing rows default to false — every car already stored has a reading its owner gave.
alter table public.cars
    add column if not exists odometer_pending boolean not null default false;

comment on column public.cars.odometer_pending is
    'The owner has not given a reading yet, so current_odometer_km is a placeholder zero '
    'rather than a measurement. Nothing may compute distance from a row where this is true.';
