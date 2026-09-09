-- `challan_vehicles` + `challans` — the challan records source, until a government API
-- replaces `SupabaseChallanRemoteDataSource`.
--
-- Two tables because "no vehicle found" must be answerable: the registry says which
-- plates the records know at all, the challans are the notices on them. A plate in the
-- registry with zero challan rows is the *clean* answer, not an error.
--
-- Reference data, not owner content: rows are seeded/administered server-side, and the
-- app only reads them — except one advisory UPDATE ("I've already paid these") that flips
-- PENDING to PAID. Reads are open to any signed-in user (a buyer's lookup checks a plate
-- they do not own; the app never sends owner names, so there is nothing personal here).
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create table if not exists public.challan_vehicles (
    -- Normalized like the app stores it: uppercase, no whitespace ("MH12AB1234").
    reg_no text primary key
);

create table if not exists public.challans (
    -- The challan number itself (e.g. MH1220260814004521) — already globally unique,
    -- which is what makes the client's cache upsert idempotent.
    id              text primary key,
    reg_no          text not null references public.challan_vehicles (reg_no) on delete cascade,
    violation       text not null,
    -- Integer paise, the same reason money is integer everywhere else in the schema.
    amount_paise    bigint not null check (amount_paise >= 0),
    location        text,
    issued_on       date not null,
    -- IN_COURT is deliberately not a flavour of PENDING: a court case cannot be paid
    -- online, and every client total excludes it.
    status          text not null default 'PENDING'
                    check (status in ('PENDING', 'PAID', 'IN_COURT')),
    court_name      text,
    next_hearing_on date
);

create index if not exists idx_challans_reg_no on public.challans (reg_no, issued_on desc);

comment on table public.challan_vehicles is
    'Which plates the challan records know. A plate here with no challans rows is a clean '
    'vehicle; a plate absent is "no vehicle found".';
comment on table public.challans is
    'Traffic challans by plate. Reference data the app caches; PENDING->PAID via the '
    'app is advisory and goes away when a real government source lands.';

alter table public.challan_vehicles enable row level security;
alter table public.challans enable row level security;

-- Any signed-in user may read: the buyer's lookup is on a plate they do not own.
drop policy if exists challan_vehicles_read on public.challan_vehicles;
create policy challan_vehicles_read on public.challan_vehicles
    for select to authenticated using (true);

drop policy if exists challans_read on public.challans;
create policy challans_read on public.challans
    for select to authenticated using (true);

-- The one write the app makes: claiming pending challans are settled. Update only, no
-- insert/delete — the records themselves are administered server-side.
drop policy if exists challans_mark_paid on public.challans;
create policy challans_mark_paid on public.challans
    for update to authenticated
    using (status = 'PENDING')
    with check (status = 'PAID');

-- ---------------------------------------------------------------------------------------
-- Sample rows for manual testing (idempotent). Mirrors the design mockups: one plate with
-- pending + court challans, one clean plate, one buyer's-lookup plate. Harmless to keep in
-- any environment — these are fictional plates.
insert into public.challan_vehicles (reg_no) values
    ('MH12AB1234'), ('MH14DK8842'), ('MH12ZZ0001')
on conflict (reg_no) do nothing;

insert into public.challans
    (id, reg_no, violation, amount_paise, location, issued_on, status, court_name, next_hearing_on)
values
    ('MH1220260814004521', 'MH12AB1234', 'Red light violation',      100000, 'Baner Road, Pune', '2026-08-14', 'PENDING', null, null),
    ('MH1220260622001883', 'MH12AB1234', 'No parking',                50000, 'FC Road, Pune',    '2026-06-22', 'PENDING', null, null),
    ('MH1220251102000914', 'MH12AB1234', 'Driving without licence',  500000, 'Shivajinagar, Pune', '2025-11-02', 'IN_COURT', 'Shivajinagar, Pune', '2026-09-04'),
    ('MH1220250918003402', 'MH12AB1234', 'Over-speeding',            100000, 'Nashik Highway',   '2025-09-18', 'PAID', null, null),
    ('MH1420260701002214', 'MH14DK8842', 'Over-speeding',            200000, 'Nashik Highway',   '2026-07-01', 'PENDING', null, null),
    ('MH1420260415000772', 'MH14DK8842', 'Signal jump',              100000, 'Wakad, Pune',      '2026-04-15', 'PENDING', null, null),
    ('MH1420251208000391', 'MH14DK8842', 'No parking',                20000, 'Baner Road, Pune', '2025-12-08', 'PENDING', null, null)
on conflict (id) do nothing;
