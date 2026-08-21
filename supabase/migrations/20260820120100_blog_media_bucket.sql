-- The bucket the CMS uploads screenshots into.
--
-- Public, because every file in it ends up inside a published article and a
-- signed URL that expires would be a broken image in a post that is still up.
-- What is not public is *writing*: only an account carrying `blog_author` can
-- put a file here, which is the same gate `blog_media` rows sit behind.
--
-- Separate from the app's own buckets on purpose. Those hold an owner's bills
-- and documents and are private per owner; nothing about their policies should
-- be reused by reflex for files meant to be seen by strangers.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'blog-media',
  'blog-media',
  true,
  -- The design says 8 MB, and the limit belongs where it can actually be
  -- enforced rather than only in the copy under the dropzone.
  8388608,
  array['image/png', 'image/jpeg']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists blog_media_public_read on storage.objects;
create policy blog_media_public_read on storage.objects
  for select using (bucket_id = 'blog-media');

drop policy if exists blog_media_author_insert on storage.objects;
create policy blog_media_author_insert on storage.objects
  for insert with check (bucket_id = 'blog-media' and public.is_blog_author());

drop policy if exists blog_media_author_update on storage.objects;
create policy blog_media_author_update on storage.objects
  for update using (bucket_id = 'blog-media' and public.is_blog_author());

drop policy if exists blog_media_author_delete on storage.objects;
create policy blog_media_author_delete on storage.objects
  for delete using (bucket_id = 'blog-media' and public.is_blog_author());
