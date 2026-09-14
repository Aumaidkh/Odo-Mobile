-- Corrections to the social section, all of them things the code review caught.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. The audit trail was writing every secret out in plaintext.
--
-- `admin_audit()` inserts `to_jsonb(new)` — the whole row, `value` column included — into
-- `admin_audit_log`, which anybody holding `audit.read` may select. So a token set from the
-- panel was readable in the Audit section a second later.
--
-- That defeats the one property the credentials table exists for. It is written through a
-- function and never selected back precisely so that an admin session cannot leak an
-- Instagram token, and the audit trigger handed it over anyway.
--
-- Audited still, because "who changed a key and when" is exactly what an audit trail is for.
-- Only the value is dropped.
-- ─────────────────────────────────────────────────────────────────────────────

drop trigger if exists trg_social_credentials_audit on public.social_credentials;

create or replace function public.social_credentials_audit()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare v_row jsonb;
begin
    -- The key and the timestamp, never the secret. A redacted marker rather than an absent
    -- field, so a reader can tell this row was scrubbed on purpose.
    v_row := jsonb_build_object(
        'key', coalesce(new.key, old.key),
        'value', '[redacted]',
        'updated_at', coalesce(new.updated_at, old.updated_at)
    );

    insert into public.admin_audit_log (
        actor_admin_id, action, subject_type, subject_id, before, after
    )
    values (
        public.current_admin_id(),
        tg_op,
        tg_table_name,
        coalesce(new.key, old.key),
        case when tg_op = 'INSERT' then null else jsonb_build_object('key', old.key, 'value', '[redacted]') end,
        case when tg_op = 'DELETE' then null else v_row end
    );
    return coalesce(new, old);
end;
$function$;

create trigger trg_social_credentials_audit
    after insert or update or delete on public.social_credentials
    for each row execute function public.social_credentials_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. `approved` is a state the pipeline acts on now.
--
-- The comment on set_social_queue_status claimed "'approved' is what the publisher picks up",
-- and nothing picked it up: the renderer takes `draft` and the Telegram button publishes only
-- from `rendered`. Approving from the panel moved a post into a state no component read, and
-- the Telegram button then refused it as already handled.
--
-- `tick` publishes `approved` rows now, which is what makes both the panel's button and
-- auto mode work. The comment is corrected rather than deleted because the next reader needs
-- to know which component owns the state.
-- ─────────────────────────────────────────────────────────────────────────────

comment on function public.set_social_queue_status(bigint, text) is
    'Moves a queued post between the states the pipeline understands. `approved` is picked up '
    'by the social-tick function, which publishes it on its next pass — not by the renderer, '
    'and not by the Telegram button, which publishes from `rendered` on the spot.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. The first recipient added must be able to approve.
--
-- `mayApprove` falls back to the environment's chat id only while the table is empty. The
-- panel's add form defaults `can_approve` to false, so adding a teammate "just to watch"
-- inserted the first row, ended the fallback, and left the owner's own chat unable to press
-- the button that publishes — with nothing else able to publish either.
--
-- A trigger rather than a constraint: the rule is about the table as a whole — "somebody can
-- approve" — which no per-row check can express. Later rows may be watchers.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.social_recipients_need_an_approver()
returns trigger
language plpgsql
as $function$
begin
    if tg_op in ('INSERT', 'UPDATE')
       and not exists (select 1 from public.social_telegram_recipients where can_approve)
       and not coalesce(new.can_approve, false) then
        raise exception
            'the first Telegram recipient must be able to approve, or nobody can publish';
    end if;
    return new;
end;
$function$;

drop trigger if exists trg_social_recipients_approver on public.social_telegram_recipients;
create trigger trg_social_recipients_approver
    before insert or update on public.social_telegram_recipients
    for each row execute function public.social_recipients_need_an_approver();
