-- Lets an admin holding `blog.write` work the blog from `/admin` (part of #370).
--
-- This is the *additive* half of folding the CMS in. The existing `is_blog_author()` policies
-- are left exactly as they are, so `blog-session` and the CMS at `/blog/admin` keep working
-- unchanged while both surfaces exist.
--
-- That matters: permissive policies OR together, so adding these widens access to admins
-- without narrowing it for authors. Replacing `is_blog_author()` outright — which is what
-- #370 eventually does — would break the running CMS the moment it applied, because
-- `blog-session` stamps a claim and `admin_has()` reads a table. The swap has to happen in
-- the same deploy that retires `/blog/admin`, not before it.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

drop policy if exists blog_posts_admin_all on public.blog_posts;
create policy blog_posts_admin_all on public.blog_posts
    for all to authenticated
    using (public.admin_has('blog.write'))
    with check (public.admin_has('blog.write'));

drop policy if exists blog_authors_admin_all on public.blog_authors;
create policy blog_authors_admin_all on public.blog_authors
    for all to authenticated
    using (public.admin_has('blog.write'))
    with check (public.admin_has('blog.write'));

drop policy if exists blog_categories_admin_all on public.blog_categories;
create policy blog_categories_admin_all on public.blog_categories
    for all to authenticated
    using (public.admin_has('blog.write'))
    with check (public.admin_has('blog.write'));

-- Publishing and unpublishing are the two changes worth being able to trace: one puts a page
-- on the public internet and the other takes it down, and both are things somebody may have
-- to explain. An ordinary draft save is not audited — an author saves a post dozens of times
-- and the log would be nothing else.
create or replace function public.blog_post_status_audit()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
    insert into public.admin_audit_log (actor_admin_id, action, subject_type, subject_id, before, after)
    values (
        public.current_admin_id(),
        case when new.status = 'published' then 'PUBLISH' else 'UNPUBLISH' end,
        'blog_posts',
        new.id::text,
        jsonb_build_object('status', old.status),
        jsonb_build_object('status', new.status, 'slug', new.slug)
    );
    return null;
end;
$function$;

drop trigger if exists trg_blog_posts_status_audit on public.blog_posts;
create trigger trg_blog_posts_status_audit
    after update of status on public.blog_posts
    for each row
    when (old.status is distinct from new.status)
    execute function public.blog_post_status_audit();
