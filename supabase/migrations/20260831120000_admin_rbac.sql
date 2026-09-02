-- The admin panel's permission model: who is staff, what roles exist, what each role may do,
-- and a record of everything they did.
--
-- Four tables and three functions. The important one is `admin_has(permission)`, which every
-- policy in every later admin migration calls. It is checked **live, at query time**, against
-- these tables — deliberately not read out of a JWT claim the way `is_blog_author()` is.
-- A claim is a snapshot taken when the token was minted, so revoking somebody's access would
-- not take effect until their next refresh, up to an hour later. That is an hour too long for
-- the one surface that can edit other people's entitlements.
--
-- The `odo_admin` claim that `admin-session` stamps is still useful, but only as a coarse
-- "is this staff at all" marker. It never answers a specific permission question.
--
-- Nothing here grants anyone anything. The three roles below arrive with their permission
-- rows and nobody holding any of them — see supabase/seed_admin.sql for the one row that has
-- to be inserted by hand, once, to break the chicken-and-egg.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- Who is staff.
--
-- Keyed by **email**, not by `auth.users.id`, and this is the whole reason adding a new admin
-- is possible at all. An admin's Supabase account does not exist until the first time they
-- sign in, and `admin-session` refuses to sign in anybody who is not already on this list —
-- so a table keyed by account id could never have its first row for a new person. The email
-- is what a super-admin knows in advance; `user_id` is filled in by the function on first
-- sign-in and is what every permission check actually matches on.
--
-- `is_active` rather than deleting a row: revoking access should leave the audit log's
-- foreign keys intact, and "who used to have this" is a question worth being able to answer.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.admin_users (
    id         uuid        primary key default gen_random_uuid(),

    -- Lower-cased on the way in, enforced rather than assumed: an address stored as
    -- "Someone@Example.com" would never match the function's lower-cased lookup, and the
    -- symptom is a new admin who is on the list and still cannot sign in.
    email      text        not null unique check (email = lower(email)),

    -- Null until the first sign-in binds an account to this row. `on delete set null` so
    -- deleting the auth account revokes the binding without erasing the allowlist entry.
    user_id    uuid        unique references auth.users (id) on delete set null,

    name       text,
    is_active  boolean     not null default true,
    created_at timestamptz not null default now()
);

comment on table public.admin_users is
    'The staff allowlist. `admin-session` refuses to mint a session for an address that is '
    'not here with is_active = true. Keyed by email because a row has to exist before the '
    'person has an account; user_id is bound on first sign-in and is what admin_has() joins on.';


-- ─────────────────────────────────────────────────────────────────────────────
-- Roles and what they may do.
--
-- Permissions are rows rather than an enum so a fourth role, or one more thing an existing
-- role may do, is an insert instead of a migration of application code. The client mirrors
-- this vocabulary to decide what to draw in the nav; it is never what decides access.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.admin_roles (
    slug        text primary key,
    name        text not null,
    description text not null default ''
);

create table if not exists public.admin_role_permissions (
    role_slug  text not null references public.admin_roles (slug) on delete cascade,
    permission text not null,
    primary key (role_slug, permission)
);

create table if not exists public.admin_user_roles (
    admin_id   uuid        not null references public.admin_users (id) on delete cascade,
    role_slug  text        not null references public.admin_roles (slug) on delete restrict,

    -- Who granted it. Null only for the seeded first super-admin, which nobody granted.
    granted_by uuid        references public.admin_users (id),
    granted_at timestamptz not null default now(),
    primary key (admin_id, role_slug)
);

comment on column public.admin_user_roles.role_slug is
    'on delete restrict, not cascade: deleting a role that people still hold should fail '
    'loudly rather than quietly stripping them of access.';


-- ─────────────────────────────────────────────────────────────────────────────
-- The audit log.
--
-- Written by triggers, never by a client (see `admin_audit()` below). A client that forgets
-- to log, or is patched to skip it, cannot. There is no insert, update or delete policy on
-- this table for anybody — only the definer-owned trigger function writes here, and nothing
-- at all rewrites or removes what it wrote.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.admin_audit_log (
    id             bigserial   primary key,

    -- Null when a service-role script made the change rather than a signed-in admin. Worth
    -- recording as "nobody" rather than refusing the write: an unattributed change still has
    -- to be visible, and failing the trigger would roll back the change it was auditing.
    actor_admin_id uuid        references public.admin_users (id) on delete set null,

    action         text        not null,   -- INSERT | UPDATE | DELETE
    subject_type   text        not null,   -- the table name
    subject_id     text,                   -- best-effort key of the affected row
    before         jsonb,
    after          jsonb,
    at             timestamptz not null default now()
);

create index if not exists idx_admin_audit_log_at      on public.admin_audit_log (at desc);
create index if not exists idx_admin_audit_log_subject on public.admin_audit_log (subject_type, subject_id);
create index if not exists idx_admin_audit_log_actor   on public.admin_audit_log (actor_admin_id, at desc);


-- ─────────────────────────────────────────────────────────────────────────────
-- The checks.
--
-- All three are `security definer`, and that is not optional. The policies below protect
-- `admin_users` with a predicate that reads `admin_users` — as an invoker-rights function
-- that is infinite recursion, and Postgres reports it as a policy error that reads like the
-- table is broken. A definer function owned by the migration's role bypasses RLS on the
-- tables it reads, which breaks the loop.
--
-- `set search_path` is likewise not optional on a definer function: without it a schema
-- earlier on the caller's path can shadow `public` and decide what these read.
--
-- `stable`, so the planner evaluates them once per statement rather than once per row.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $function$
    select exists (
        select 1 from public.admin_users
         where user_id = auth.uid() and is_active
    )
$function$;

comment on function public.is_admin() is
    'Is the caller staff at all. Coarse: it is what lets an admin read the admin tables so '
    'the nav can ask what it may show. Never use it to gate a write — use admin_has().';

create or replace function public.admin_has(p_permission text)
returns boolean
language sql
stable
security definer
set search_path = public
as $function$
    select exists (
        select 1
          from public.admin_users u
          join public.admin_user_roles ur on ur.admin_id = u.id
          join public.admin_role_permissions rp on rp.role_slug = ur.role_slug
         where u.user_id = auth.uid()
           and u.is_active
           and rp.permission = p_permission
    )
$function$;

comment on function public.admin_has(text) is
    'The one permission check. Every admin-gated policy in every later migration calls this. '
    'An unknown permission string returns false — fail closed, which is the opposite of the '
    'AppStatus gate and deliberately so: that one protects a running app, this one protects '
    'other people''s data.';

create or replace function public.current_admin_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $function$
    select id from public.admin_users where user_id = auth.uid() and is_active
$function$;

-- Not callable by a stranger. Nothing anonymous has a legitimate reason to ask these, and a
-- signed-out caller getting a plain `false` invites the answer being treated as data. The
-- policies below are all scoped `to authenticated`, so no anon query ever needs to evaluate
-- one of these.
revoke all on function public.is_admin()               from public, anon;
revoke all on function public.admin_has(text)          from public, anon;
revoke all on function public.current_admin_id()       from public, anon;
grant execute on function public.is_admin()            to authenticated;
grant execute on function public.admin_has(text)       to authenticated;
grant execute on function public.current_admin_id()    to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- The audit trigger.
--
-- One generic function attached to every table worth recording. `security definer` so it can
-- write to a table nobody has an insert policy on, and so the write cannot be skipped by
-- whatever the client did or did not send.
--
-- Returns null: it is an AFTER trigger, where the return value is discarded anyway, and
-- saying so is clearer than returning a row that goes nowhere.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_audit()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
    v_before jsonb := case when tg_op = 'INSERT' then null else to_jsonb(old) end;
    v_after  jsonb := case when tg_op = 'DELETE' then null else to_jsonb(new) end;
    v_row    jsonb := coalesce(v_after, v_before);
begin
    insert into public.admin_audit_log (
        actor_admin_id, action, subject_type, subject_id, before, after
    )
    values (
        public.current_admin_id(),
        tg_op,
        tg_table_name,
        -- Best effort, in the order these tables actually key themselves. A composite-key
        -- table like admin_user_roles has no `id`, so its admin_id is the useful handle;
        -- `slug` covers admin_roles. Null when none of them fit, which is still a row worth
        -- logging — the before/after payloads carry the whole truth regardless.
        coalesce(v_row ->> 'id', v_row ->> 'admin_id', v_row ->> 'owner_id', v_row ->> 'slug'),
        v_before,
        v_after
    );
    return null;
end;
$function$;

comment on function public.admin_audit() is
    'Generic audit trigger. Attach to any table an admin can write. Records the acting admin '
    'from the session, or null for a service-role change — an unattributed change is still '
    'logged rather than refused, because failing here would roll back the change being audited.';

drop trigger if exists trg_admin_users_audit on public.admin_users;
create trigger trg_admin_users_audit
    after insert or update or delete on public.admin_users
    for each row execute function public.admin_audit();

drop trigger if exists trg_admin_user_roles_audit on public.admin_user_roles;
create trigger trg_admin_user_roles_audit
    after insert or update or delete on public.admin_user_roles
    for each row execute function public.admin_audit();

drop trigger if exists trg_admin_roles_audit on public.admin_roles;
create trigger trg_admin_roles_audit
    after insert or update or delete on public.admin_roles
    for each row execute function public.admin_audit();

drop trigger if exists trg_admin_role_permissions_audit on public.admin_role_permissions;
create trigger trg_admin_role_permissions_audit
    after insert or update or delete on public.admin_role_permissions
    for each row execute function public.admin_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- Row level security.
--
-- Every policy is scoped `to authenticated`. An anonymous caller matches no policy at all
-- and gets an empty result rather than an error — the same shape the blog's closed tables
-- answer with, and the same thing check-admin.sh asserts.
--
-- Reading: any active admin may read the whole model, because the nav has to ask what it is
-- allowed to show. Writing: `admin.roles.write`, which only super-admin holds.
-- ─────────────────────────────────────────────────────────────────────────────

alter table public.admin_users            enable row level security;
alter table public.admin_roles            enable row level security;
alter table public.admin_role_permissions enable row level security;
alter table public.admin_user_roles       enable row level security;
alter table public.admin_audit_log        enable row level security;

drop policy if exists admin_users_read on public.admin_users;
create policy admin_users_read on public.admin_users
    for select to authenticated
    using (public.is_admin());

drop policy if exists admin_users_write on public.admin_users;
create policy admin_users_write on public.admin_users
    for all to authenticated
    using (public.admin_has('admin.roles.write'))
    with check (public.admin_has('admin.roles.write'));

drop policy if exists admin_roles_read on public.admin_roles;
create policy admin_roles_read on public.admin_roles
    for select to authenticated
    using (public.is_admin());

drop policy if exists admin_roles_write on public.admin_roles;
create policy admin_roles_write on public.admin_roles
    for all to authenticated
    using (public.admin_has('admin.roles.write'))
    with check (public.admin_has('admin.roles.write'));

drop policy if exists admin_role_permissions_read on public.admin_role_permissions;
create policy admin_role_permissions_read on public.admin_role_permissions
    for select to authenticated
    using (public.is_admin());

drop policy if exists admin_role_permissions_write on public.admin_role_permissions;
create policy admin_role_permissions_write on public.admin_role_permissions
    for all to authenticated
    using (public.admin_has('admin.roles.write'))
    with check (public.admin_has('admin.roles.write'));

drop policy if exists admin_user_roles_read on public.admin_user_roles;
create policy admin_user_roles_read on public.admin_user_roles
    for select to authenticated
    using (public.is_admin());

drop policy if exists admin_user_roles_write on public.admin_user_roles;
create policy admin_user_roles_write on public.admin_user_roles
    for all to authenticated
    using (public.admin_has('admin.roles.write'))
    with check (public.admin_has('admin.roles.write'));

-- Read-only, and only for a role that holds `audit.read`. No insert, update or delete policy
-- exists for anybody — the definer-owned trigger is the only writer, and nothing may rewrite
-- or remove what it wrote. That is the whole value of the table.
drop policy if exists admin_audit_log_read on public.admin_audit_log;
create policy admin_audit_log_read on public.admin_audit_log
    for select to authenticated
    using (public.admin_has('audit.read'));


-- ─────────────────────────────────────────────────────────────────────────────
-- The three roles at launch, and what they may do.
--
-- Insert-if-missing rather than replace: re-running this file adds a permission that has been
-- added to it since, and leaves alone anything a super-admin has granted a role by hand.
-- Taking a permission away is therefore a deliberate delete, not something a re-run does
-- behind somebody's back.
-- ─────────────────────────────────────────────────────────────────────────────

insert into public.admin_roles (slug, name, description) values
    ('super_admin', 'Super admin', 'Everything, including granting roles.'),
    ('content',     'Content',     'The blog, both catalogs, and fairness benchmark data.'),
    ('support',     'Support',     'User lookup, entitlement overrides, restriction, and the audit log.')
on conflict (slug) do nothing;

insert into public.admin_role_permissions (role_slug, permission) values
    ('super_admin', 'blog.write'),
    ('super_admin', 'catalog.vehicles.write'),
    ('super_admin', 'catalog.cities.write'),
    ('super_admin', 'fairness.write'),
    ('super_admin', 'users.read'),
    ('super_admin', 'users.entitlements.write'),
    ('super_admin', 'users.restrict.write'),
    ('super_admin', 'audit.read'),
    ('super_admin', 'admin.roles.write'),

    ('content',     'blog.write'),
    ('content',     'catalog.vehicles.write'),
    ('content',     'catalog.cities.write'),
    ('content',     'fairness.write'),

    ('support',     'users.read'),
    ('support',     'users.entitlements.write'),
    ('support',     'users.restrict.write'),
    ('support',     'audit.read')
on conflict (role_slug, permission) do nothing;
