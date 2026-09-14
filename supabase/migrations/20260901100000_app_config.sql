-- Feature flags and remote settings, in Postgres rather than Firebase Remote Config.
--
-- **Why move them.** Remote Config's API authenticates with a Google service-account private
-- key, which cannot be in a browser — so the panel reached it through an edge function holding
-- the key, and standing that up needs a key generated in one console and a role granted in
-- another. Two consoles and a secret, to change a boolean. Here the panel writes a row, RLS
-- decides who may, and `admin_audit()` records it like every other admin write.
--
-- **What this table is not.** It is not the source of a key's existence. Keys are declared in
-- Kotlin — `@Flag` in `:core:config`, assembled by KSP into `ConfigRegistry` — and each carries
-- a compiled default. A row here is an *override*: it says "this key is currently something
-- other than what shipped". A key with no row resolves to its compiled default, which is
-- exactly what happens today when Remote Config has nothing to say. That is what keeps a fresh
-- install with no network behaving correctly.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


create table if not exists public.app_config (
    -- The key as declared in Kotlin: lowercase, digits and underscores, starting with a letter.
    -- Checked here as well as by the processor, because a typo written straight into the table
    -- would be a key nothing ever reads, and silence is the wrong answer to a typo.
    key         text        primary key check (key ~ '^[a-z][a-z0-9_]*$'),

    -- Held as text whatever the type, the same form the compiled default is written in, so one
    -- parsing path serves both. `ConfigResolver` already parses strings; giving this table
    -- typed columns would mean a second parser that can disagree with the first.
    value       text        not null,

    -- BOOLEAN | INT | LONG | DOUBLE | STRING | ENUM, matching ConfigType. Advisory: the app
    -- parses by the key's *declared* type, not by this. It is here so the panel can draw a
    -- toggle instead of a text box, and so a value that cannot possibly parse is visible.
    value_type  text        not null default 'STRING'
        check (value_type in ('BOOLEAN', 'INT', 'LONG', 'DOUBLE', 'STRING', 'ENUM')),

    -- Copied from the @Flag declaration when the key is seeded. The panel shows it, because
    -- "refuel_detect_enabled" tells somebody at 2am nothing about what turning it off does.
    description text        not null default '',
    owner       text        not null default '',

    -- False parks a row without deleting it: the override stops applying and the app falls back
    -- to the compiled default, but the description and the last value are still there. Deleting
    -- would work too and lose the note explaining why it was ever set.
    is_active   boolean     not null default true,

    updated_at  timestamptz not null default now(),
    updated_by  uuid        references public.admin_users (id) on delete set null
);

comment on table public.app_config is
    'Remote overrides for keys declared by @Flag/@Value in :core:config. A key with no active '
    'row resolves to its compiled default. Not the source of a key''s existence — Kotlin is.';

comment on column public.app_config.value is
    'The override, as text in the same form the compiled default is written in. Parsed by the '
    'key''s declared type, not by value_type.';

alter table public.app_config enable row level security;


-- ─────────────────────────────────────────────────────────────────────────────
-- Who may read it.
--
-- Everybody, signed in or not, and that is deliberate. These are kill switches, not secrets:
-- every one of them is already visible in the APK as a compiled default, and the app has to be
-- able to read them before anybody signs in — a kill switch that only works for signed-in users
-- is not a kill switch. Nothing here is worth hiding, and hiding it would break the case it
-- exists for.
--
-- Only active rows are readable, so parking a row is the same as removing it as far as a device
-- is concerned.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists app_config_read on public.app_config;
create policy app_config_read on public.app_config
    for select to anon, authenticated
    using (is_active);

-- The panel needs the parked rows too — it is the screen somebody un-parks them from, and a row
-- it cannot see is a row it cannot restore. Same trap the cities catalog had.
drop policy if exists app_config_admin_read on public.app_config;
create policy app_config_admin_read on public.app_config
    for select to authenticated
    using (public.admin_has('flags.write'));

drop policy if exists app_config_admin_write on public.app_config;
create policy app_config_admin_write on public.app_config
    for all to authenticated
    using (public.admin_has('flags.write'))
    with check (public.admin_has('flags.write'));

drop trigger if exists trg_app_config_updated on public.app_config;
create trigger trg_app_config_updated before insert or update on public.app_config
    for each row execute function public.set_updated_at();

drop trigger if exists trg_app_config_audit on public.app_config;
create trigger trg_app_config_audit
    after insert or update or delete on public.app_config
    for each row execute function public.admin_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- Stamping who changed it.
--
-- Separate from the audit trigger: the audit log records the change, this records the current
-- owner of the value, which is what the panel's "changed by" column reads. Both, because the
-- log is append-only history and this is the row's own state.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.app_config_stamp_admin()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
    new.updated_by := public.current_admin_id();
    return new;
end;
$function$;

drop trigger if exists trg_app_config_stamp on public.app_config;
create trigger trg_app_config_stamp before insert or update on public.app_config
    for each row execute function public.app_config_stamp_admin();


-- ─────────────────────────────────────────────────────────────────────────────
-- The keys this app declares today.
--
-- Seeded with each key's compiled default, so the table opens showing the truth rather than
-- empty. `on conflict do nothing`: re-running must never reset a value somebody has changed,
-- which is the one thing a seed on a live table must not do.
--
-- Descriptions are the `why` from the Kotlin declaration, shortened. When a key is added in
-- Kotlin it needs a line here too — there is no way to make that automatic from SQL, and the
-- panel showing a key the app does not read would be worse than the panel not showing it.
-- ─────────────────────────────────────────────────────────────────────────────

insert into public.app_config (key, value, value_type, description, owner) values
    ('auto_odometer_enabled', 'true', 'BOOLEAN',
     'Kill switch for automatic trip tracking. Off stops the garage card, enrollment and the trip-logged redirect for everyone, with no release.',
     'platform'),
    ('refuel_detect_enabled', 'true', 'BOOLEAN',
     'Kill switch for notification-based refuel detection. Turning it off works; turning it on only works on a build whose manifest declares the listener.',
     'platform'),
    ('onboarding_video_enabled', 'true', 'BOOLEAN',
     'Whether onboarding shows its video step.',
     'growth')
on conflict (key) do nothing;
