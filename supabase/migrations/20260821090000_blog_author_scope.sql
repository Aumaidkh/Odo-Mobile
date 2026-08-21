-- Whose post is it.
--
-- `is_blog_author()` answers "may this account publish", which is not the same
-- question as "is this yours". Left as the whole policy — which is how it
-- shipped — every author saw every other author's drafts, and a draft is the one
-- thing in a CMS that is nobody else's business until its writer says so.
--
-- Its own migration rather than an edit to the first one: that file has already
-- been applied, and a schema change that pretends to have been there all along is
-- a schema change nobody can find later.

create or replace function public.current_blog_author_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select a.id
  from public.blog_authors a
  where lower(a.email) = lower(coalesce(auth.jwt() ->> 'email', ''))
  limit 1
$$;

revoke all on function public.current_blog_author_id() from public;
revoke all on function public.current_blog_author_id() from anon;
grant execute on function public.current_blog_author_id() to authenticated;

-- Stamped by the database, not sent by the client.
--
-- A client that supplies its own author_id is a client that can supply somebody
-- else's. The policy below would catch that, but a row that never carries the
-- field cannot be wrong in the first place.
create or replace function public.blog_posts_stamp_author()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.author_id is null then
    new.author_id := public.current_blog_author_id();
  end if;
  return new;
end;
$$;

drop trigger if exists blog_posts_stamp_author on public.blog_posts;
create trigger blog_posts_stamp_author
  before insert on public.blog_posts
  for each row execute function public.blog_posts_stamp_author();

-- Your own posts, and only those.
--
-- Published posts stay readable by everybody through blog_posts_read_published;
-- this policy is about writing, and about what the CMS lets you find.
drop policy if exists blog_posts_author_all on public.blog_posts;
create policy blog_posts_author_all on public.blog_posts
  for all
  using (author_id = public.current_blog_author_id())
  with check (author_id = public.current_blog_author_id());
