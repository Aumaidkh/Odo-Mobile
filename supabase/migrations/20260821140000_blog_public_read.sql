-- Let the public read the blog again.
--
-- The author-scope migration revoked execute on current_blog_author_id() from
-- anon, which was right: a visitor has no author id. What it missed is that
-- blog_posts_author_all carries no `to` clause, so Postgres applies it to every
-- role and evaluates it for anon too. Anon's select therefore called a function
-- it may not execute, and the whole read failed with 42501 — not "no drafts for
-- you" but "no blog for you". A published post has been unreadable to anybody
-- signed out since that migration landed.
--
-- Naming the role is the fix. Anon never reaches the function because the policy
-- that mentions it is no longer one of anon's policies, and the revoke stands.

drop policy if exists blog_posts_author_all on public.blog_posts;
create policy blog_posts_author_all on public.blog_posts
  for all
  to authenticated
  using (author_id = public.current_blog_author_id())
  with check (author_id = public.current_blog_author_id());

-- Said out loud rather than left to the default. This is the policy that serves
-- odoapp.in/blog to a stranger, and the roles it covers should not be something
-- a reader of this file has to infer.
drop policy if exists blog_posts_read_published on public.blog_posts;
create policy blog_posts_read_published on public.blog_posts
  for select
  to anon, authenticated
  using (status = 'published');
