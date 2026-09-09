package com.hopcape.odo.web.blog.infrastructure.supabase

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.encoded
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.platform.UploadRequest
import com.hopcape.odo.web.blog.domain.model.Analytics
import com.hopcape.odo.web.blog.domain.model.Author
import com.hopcape.odo.web.blog.domain.model.Draft
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.domain.model.PostRow as DomainPostRow
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.domain.model.PublishOutcome
import com.hopcape.odo.web.blog.domain.model.SeoDraft
import com.hopcape.odo.web.blog.domain.model.TopPost
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException

/**
 * The CMS, against Postgres.
 *
 * Every call runs as the signed-in author, and every table it touches is behind
 * `is_blog_author()`. There is no permission check in this file: a request from a
 * session without the claim comes back 403 and lands on
 * [WebError.NotSignedIn], which the screen already draws. Re-checking here would
 * be a second copy of the rule that matters.
 */
@OptIn(ExperimentalTime::class)
internal class SupabaseAdminRepository(
    private val postgrest: Postgrest,
    private val client: HttpClient,
    private val baseUrl: String,
    private val anonKey: String,
    private val accessToken: suspend () -> String?,
    /** The signed-in author's row id. Null before the session has been read. */
    private val authorId: () -> String?,
    private val today: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) : AdminRepository {

    override suspend fun posts(): Either<WebError, List<DomainPostRow>> =
        postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            // Your own, published first and then by reach — the order the design's
            // table is in. Scoped explicitly rather than left to RLS: the policy
            // also lets an author read every *published* post, which is right for
            // the public site and wrong for a page called "your posts".
            query = "select=*&author_id=eq.${authorId() ?: NOBODY}&order=status.asc,views.desc",
        ).map { rows ->
            rows.map { row ->
                DomainPostRow(
                    id = row.id,
                    // Blank stays blank. What to call a post with no title is a
                    // decision about copy, and copy belongs to the screen.
                    title = row.title,
                    slug = row.slug.takeIf { row.postStatus == PostStatus.PUBLISHED },
                    status = row.postStatus,
                    views = row.views.toInt().takeIf { row.postStatus == PostStatus.PUBLISHED },
                    // No clock arithmetic: the column is a timestamp and turning it
                    // into "2 days ago" needs a locale and a now. Until there is a
                    // formatter worth the name, the date is more honest than a
                    // guess dressed as a phrase — and an absent date is left empty
                    // for the screen to label.
                    updatedLabel = row.publishedOn?.take(10).orEmpty(),
                )
            }
        }

    override suspend fun draft(id: String?): Either<WebError, Draft> {
        if (id == null) return emptyDraft().right()
        return postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "select=*&id=eq.$id&limit=1",
        ).flatMap { rows ->
            rows.firstOrNull()?.toDraft()?.right() ?: WebError.NotFound.left()
        }
    }

    override suspend fun save(draft: Draft): Either<WebError, Draft> =
        postgrest.upsert(
            table = "blog_posts",
            body = "[${draft.asJson(status = draft.status, publishedOn = null)}]",
            serializer = PostRow.serializer(),
        ).flatMap { rows ->
            rows.firstOrNull()?.toDraft()?.right()
                ?: WebError.Unexpected("save returned no row").left()
        }.onRight { if (it.status == PostStatus.PUBLISHED) requestRebuild() }

    /**
     * Publishing, and the one thing that can stop it.
     *
     * The slug is checked before the write rather than after a unique-violation,
     * because the answer is not "that failed" — it is a choice between two ways
     * forward, and one of them needs the title of the post already at that URL.
     */
    override suspend fun publish(draft: Draft, replaceExisting: Boolean): Either<WebError, PublishOutcome> = either {
        val slug = draft.seo.slug.trim().ifBlank { draft.title.slugify() }

        val holder = postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "select=id,title,slug,status&slug=eq.${slug.encoded()}&status=eq.published&limit=1",
        ).bind().firstOrNull()?.takeIf { it.id != draft.id }

        if (holder != null && !replaceExisting) {
            return@either PublishOutcome.SlugTaken(
                slug = slug,
                heldBy = holder.title,
                suggestion = "$slug-2",
            )
        }

        if (holder != null) {
            // Taking the URL over. The old post keeps its body and its id; what it
            // loses is the address, which is why this is never automatic.
            postgrest.patch(
                table = "blog_posts",
                query = "id=eq.${holder.id}",
                body = """{"status":"draft","slug":null}""",
            ).bind()
        }

        postgrest.upsert(
            table = "blog_posts",
            body = "[${draft.copy(seo = draft.seo.copy(slug = slug)).asJson(PostStatus.PUBLISHED, today())}]",
            serializer = PostRow.serializer(),
        ).bind()

        requestRebuild()
        PublishOutcome.Published(slug)
    }

    override suspend fun unpublish(id: String): Either<WebError, Unit> =
        // The slug goes with it, so the URL is free for whatever takes its place.
        // Keeping it would make the post unpublishable-and-unreplaceable at once.
        postgrest.patch(
            table = "blog_posts",
            query = "id=eq.$id",
            body = """{"status":"draft","slug":null}""",
        ).onRight { requestRebuild() }

    override suspend fun discard(id: String): Either<WebError, Unit> =
        // RLS decides whether this row is yours; nothing here needs to ask.
        postgrest.delete(table = "blog_posts", query = "id=eq.$id").onRight { requestRebuild() }

    /**
     * Tells the site to rebuild itself.
     *
     * The pages a stranger reads are HTML files generated at deploy time, so a
     * post is not really published until something regenerates them. This asks
     * an edge function to start that; the function holds the GitHub credential,
     * because a token that can deploy a website should never be inside a page
     * anyone can open.
     *
     * Failure is swallowed on purpose. The post is already saved and the write
     * that mattered has happened — refusing to report success because a rebuild
     * could not be scheduled would tell the author their work was lost, which is
     * false. A missed rebuild costs freshness until the next one, and the
     * workflow can always be run by hand.
     */
    private suspend fun requestRebuild() {
        try {
            client.post("$baseUrl/functions/v1/blog-rebuild") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer ${accessToken() ?: anonKey}")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Offline, or the function is not deployed yet. Neither is the
            // author's problem and neither undoes the write.
        }
    }

    override suspend fun media(): Either<WebError, List<MediaItem>> =
        postgrest.select("blog_media", MediaRow.serializer(), "order=created_at.desc")
            .map { rows ->
                rows.map { MediaItem(name = it.name, url = publicUrl(it.path), altText = it.altText) }
            }

    /**
     * The file, then the row.
     *
     * In that order deliberately: a row pointing at a file that failed to upload is
     * a broken image in the library, while a file with no row is invisible and
     * harmless. Neither is good, and only one of them shows up in an article.
     */
    override suspend fun upload(file: UploadRequest): Either<WebError, MediaItem> = either {
        // Prefixed with the date, so two screenshots called `home.png` from
        // different weeks do not fight over one path.
        val path = "${today()}/${file.name.sanitised()}"

        val response = runCatching {
            client.post("$baseUrl/storage/v1/object/blog-media/$path") {
                header("apikey", anonKey)
                header(HttpHeaders.Authorization, "Bearer ${accessToken() ?: anonKey}")
                header("x-upsert", "true")
                contentType(ContentType.parse(file.mimeType))
                setBody(file.bytes)
            }
        }.getOrNull() ?: raise(WebError.Offline)

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            raise(WebError.Unexpected("upload ${response.status.value}: ${body.take(200)}"))
        }

        postgrest.upsert(
            table = "blog_media",
            body = """[{"name":"${file.name.jsonEscaped()}","path":"${path.jsonEscaped()}"}]""",
            serializer = MediaRow.serializer(),
            onConflict = "path",
        ).bind()

        MediaItem(name = file.name, url = publicUrl(path))
    }

    override suspend fun profile(): Either<WebError, Author> =
        postgrest.select(
            table = "blog_authors",
            serializer = AuthorRow.serializer(),
            query = "select=*&id=eq.${authorId() ?: NOBODY}&limit=1",
        ).flatMap { rows ->
            rows.firstOrNull()?.toAuthor()?.right() ?: WebError.NotFound.left()
        }

    override suspend fun saveProfile(
        name: String,
        bio: String,
        topics: String,
        since: String,
    ): Either<WebError, Author> =
        // A patch, not an upsert: the row exists — blog-session made it on the
        // first sign-in — and the columns not named here (email, slug) are not
        // this form's to touch.
        postgrest.patch(
            table = "blog_authors",
            query = "id=eq.${authorId() ?: NOBODY}",
            body = """{"name":"${name.jsonEscaped()}","initial":"${name.take(1).uppercase()}",""" +
                """"bio":"${bio.jsonEscaped()}","topics":"${topics.jsonEscaped()}",""" +
                """"since_label":"${since.jsonEscaped()}"}""",
        ).flatMap { profile() }

    override suspend fun analytics(): Either<WebError, Analytics> = either {
        val window = postgrest.rpc(
            name = "blog_analytics",
            body = """{"p_days":$WINDOW_DAYS}""",
            serializer = AnalyticsRow.serializer(),
        ).bind().firstOrNull() ?: AnalyticsRow()

        val top = postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "select=title,views&status=eq.published&order=views.desc&limit=$TOP_POSTS",
        ).bind().map { TopPost(title = it.title, views = it.views.toInt()) }

        Analytics(
            windowLabel = "Last $WINDOW_DAYS days",
            views = window.views.toInt(),
            searchSharePercent = if (window.views == 0L) 0 else ((window.fromSearch * 100) / window.views).toInt(),
            // Nothing here knows about installs. Play attribution is the only
            // source and it does not reach this database, so the screen shows a
            // dash rather than a number nobody can stand behind.
            appInstalls = null,
            topPosts = top,
        )
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun PostRow.toDraft(): Draft = Draft(
        id = id,
        title = title,
        body = blocks(),
        status = postStatus,
        seo = SeoDraft(
            seoTitle = seoTitle,
            slug = slug.orEmpty(),
            metaDescription = metaDescription,
            categorySlug = categorySlug,
        ),
        wordCount = wordCount,
        readingMinutes = readingMinutes,
    )

    /**
     * A draft as a row.
     *
     * `id` is omitted when there is none, so Postgres generates one and the upsert
     * becomes an insert. Every other column is written, including the nulls:
     * PostgREST reads an absent key as "leave this alone", so a cleared meta
     * description would silently keep the old text.
     */
    private fun Draft.asJson(status: PostStatus, publishedOn: LocalDate?): String {
        val fields = buildList {
            id?.let { add(""""id":"$it"""") }
            add(""""title":"${title.jsonEscaped()}"""")
            add(""""dek":"${seo.metaDescription.jsonEscaped()}"""")
            add(""""status":"${if (status == PostStatus.PUBLISHED) "published" else "draft"}"""")
            add(""""body":${encodeBlocks(body)}""")
            add(""""seo_title":"${seo.seoTitle.jsonEscaped()}"""")
            add(""""meta_description":"${seo.metaDescription.jsonEscaped()}"""")
            add(""""word_count":$wordCount""")
            add(""""reading_minutes":$readingMinutes""")
            add(""""category_slug":${seo.categorySlug?.let { "\"$it\"" } ?: "null"}""")
            if (status == PostStatus.PUBLISHED) {
                add(""""slug":"${seo.slug.jsonEscaped()}"""")
                add(""""published_on":"${publishedOn ?: today()}"""")
            }
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun publicUrl(path: String): String = "$baseUrl/storage/v1/object/public/blog-media/$path"

    private fun emptyDraft(): Draft = Draft(
        id = null,
        title = "",
        body = emptyList(),
        status = PostStatus.DRAFT,
        seo = SeoDraft(),
        wordCount = 0,
        readingMinutes = 0,
    )

    private fun String.slugify(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "untitled" }

    /** A storage path may not carry a slash or a space and stay one object. */
    private fun String.sanitised(): String =
        lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifEmpty { "image.png" }

    private companion object {
        const val WINDOW_DAYS = 30
        const val TOP_POSTS = 4
        /** Matches nothing, for the moment before the session has been read. */
        const val NOBODY = "00000000-0000-0000-0000-000000000000"
    }
}

private inline fun <A, B> Either<WebError, A>.flatMap(block: (A) -> Either<WebError, B>): Either<WebError, B> =
    fold({ it.left() }, block)
