package com.hopcape.odo.web.blog.data

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.domain.BlogError
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.UploadRequest
import com.hopcape.odo.web.blog.domain.model.Analytics
import com.hopcape.odo.web.blog.domain.model.Article
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.Author
import com.hopcape.odo.web.blog.domain.model.AuthorPage
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.CategoryPage
import com.hopcape.odo.web.blog.domain.model.Draft
import com.hopcape.odo.web.blog.domain.model.IndexPage
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.domain.model.PostRow
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.domain.model.PublishOutcome
import com.hopcape.odo.web.blog.domain.model.SearchResults
import com.hopcape.odo.web.blog.domain.model.SeoDraft
import com.hopcape.odo.web.blog.domain.model.Session
import kotlinx.coroutines.delay

/**
 * The repositories, backed by [SampleContent] until there is a backend.
 *
 * They are ordinary implementations of the ports, not stubs that return
 * constants: each one does the filtering, matching and validation a real query
 * would do. That is what makes them worth having — a screen that only ever saw a
 * fixed list would not have working search, would never render an empty state,
 * and would break on the day the data started varying.
 *
 * Each read waits [READ_DELAY] first. Without it every screen resolves inside one
 * frame, the loading state never appears on screen, and nobody notices it was
 * never designed. Over a real connection it will always appear.
 */
private const val READ_DELAY: Long = 140

/** Writes take a little longer, so a saving state is visible too. */
private const val WRITE_DELAY: Long = 320

internal class SampleBlogRepository : BlogRepository {

    override suspend fun categories(): Either<BlogError, List<Category>> {
        delay(READ_DELAY)
        return SampleContent.categories.right()
    }

    override suspend fun index(): Either<BlogError, IndexPage> {
        delay(READ_DELAY)
        return IndexPage(
            lead = SampleContent.posts.firstOrNull(),
            rest = SampleContent.posts.drop(1).take(4),
            categories = SampleContent.categories,
        ).right()
    }

    override suspend fun article(slug: String): Either<BlogError, Article> {
        delay(READ_DELAY)
        val summary = SampleContent.posts.firstOrNull { it.slug == slug }
            ?: return BlogError.NotFound.left()
        return Article(
            summary = summary,
            author = SampleContent.rahul,
            body = SampleContent.bodies[slug].orEmpty(),
            // Two posts from other categories: somebody who finished this one is
            // done with this topic, and the next click should widen, not repeat.
            readNext = SampleContent.posts
                .filter { it.slug != slug && it.category != summary.category }
                .take(2),
        ).right()
    }

    override suspend fun category(slug: String): Either<BlogError, CategoryPage> {
        delay(READ_DELAY)
        val category = SampleContent.categories.firstOrNull { it.slug == slug }
            ?: return BlogError.NotFound.left()
        return CategoryPage(
            category = category,
            posts = SampleContent.posts.filter { it.category.slug == slug },
        ).right()
    }

    override suspend fun author(slug: String): Either<BlogError, AuthorPage> {
        delay(READ_DELAY)
        if (slug != SampleContent.rahul.slug) return BlogError.NotFound.left()
        return AuthorPage(SampleContent.rahul, SampleContent.posts).right()
    }

    override suspend fun search(query: String): Either<BlogError, SearchResults> {
        delay(READ_DELAY)
        val term = query.trim()
        if (term.isEmpty()) return SearchResults(query, emptyList(), emptyList()).right()

        val hits = SampleContent.posts.filter { it.matches(term) }
        return SearchResults(
            query = term,
            hits = hits,
            // Only when there is nothing: suggestions next to results would read
            // as more results, and the design only shows them on a dead end.
            suggestions = if (hits.isNotEmpty()) emptyList() else suggestionsFor(term),
        ).right()
    }

    override suspend fun mostRead(limit: Int): Either<BlogError, List<PostSummary>> {
        delay(READ_DELAY)
        return SampleContent.posts
            .sortedByDescending { SampleContent.views[it.slug] ?: 0 }
            .take(limit)
            .right()
    }

    override suspend fun subscribe(email: String): Either<BlogError, Unit> {
        delay(WRITE_DELAY)
        return if (email.looksLikeEmail()) Unit.right() else BlogError.Unexpected("bad address").left()
    }

    override suspend fun requestTopic(email: String, query: String): Either<BlogError, Unit> {
        delay(WRITE_DELAY)
        return if (email.looksLikeEmail()) Unit.right() else BlogError.Unexpected("bad address").left()
    }

    /**
     * A hit needs *every* word of the term, not any of them.
     *
     * "tyre warranty claim" must find nothing: a post about tyre prices is not an
     * answer about warranty claims, and returning it as a result would be worse
     * than the dead end — the reader reads the wrong article and leaves. Any-word
     * matching is what [suggestionsFor] does, under a heading that says so.
     */
    private fun PostSummary.matches(term: String): Boolean =
        term.split(' ').filter { it.isNotBlank() }.all { word -> containsWord(word) }

    private fun PostSummary.containsWord(word: String): Boolean =
        title.contains(word, ignoreCase = true) ||
            dek.contains(word, ignoreCase = true) ||
            category.name.contains(word, ignoreCase = true)

    /**
     * What to offer when nothing matched.
     *
     * The design's example is "tyre warranty claim" returning tyre and mileage
     * posts, so this looks for any single word of the term anywhere, and falls
     * back to the most-read rather than showing an empty page twice over.
     */
    private fun suggestionsFor(term: String): List<PostSummary> {
        val loose = SampleContent.posts.filter { post ->
            term.split(' ').any { word -> word.length > 3 && post.containsWord(word) }
        }
        return (loose.ifEmpty {
            SampleContent.posts.sortedByDescending { SampleContent.views[it.slug] ?: 0 }
        }).take(3)
    }
}

internal class SampleAuthRepository : AuthRepository {

    private var session: Session? = null

    /** Counts down the way the design's error does, and does not reset on its own. */
    private var triesLeft: Int = TRIES

    override suspend fun session(): Either<BlogError, Session?> {
        delay(READ_DELAY)
        return session.right()
    }

    override suspend fun signIn(email: String, password: String): Either<BlogError, Session> {
        delay(WRITE_DELAY)
        val correct = email.trim().equals(SampleContent.SIGN_IN_EMAIL, ignoreCase = true) &&
            password == SampleContent.SIGN_IN_PASSWORD
        if (!correct) {
            triesLeft = (triesLeft - 1).coerceAtLeast(0)
            return BlogError.SignInRejected(triesLeft).left()
        }
        triesLeft = TRIES
        session = SampleContent.session
        return SampleContent.session.right()
    }

    override suspend fun signOut(): Either<BlogError, Unit> {
        session = null
        return Unit.right()
    }

    private companion object {
        const val TRIES = 3
    }
}

internal class SampleAdminRepository(
    private val auth: AuthRepository,
) : AdminRepository {

    /** Drafts and published posts, by id. Edits survive until the page reloads. */
    private val stored: MutableMap<String, Draft> = buildMap {
        SampleContent.posts.forEach { post ->
            put(post.slug, post.asDraft(PostStatus.PUBLISHED))
        }
        SampleContent.drafts.forEach { (id, title, _) ->
            put(id, emptyDraft().copy(id = id, title = title))
        }
    }.toMutableMap()

    private var uploads: List<MediaItem> = SampleContent.media

    override suspend fun posts(): Either<BlogError, List<PostRow>> = guarded {
        stored.values
            .map { draft ->
                PostRow(
                    id = draft.id.orEmpty(),
                    title = draft.title,
                    slug = draft.seo.slug.takeIf { it.isNotBlank() && draft.status == PostStatus.PUBLISHED },
                    status = draft.status,
                    views = SampleContent.views[draft.id].takeIf { draft.status == PostStatus.PUBLISHED },
                    updatedLabel = SampleContent.updatedLabels[draft.id]
                        ?: SampleContent.drafts.firstOrNull { it.first == draft.id }?.third
                        ?: "Today",
                )
            }
            // Published first and most recent at the top, which is the order the
            // design's table is in.
            .sortedWith(compareBy({ it.status != PostStatus.PUBLISHED }, { -(it.views ?: 0) }))
            .right()
    }

    override suspend fun draft(id: String?): Either<BlogError, Draft> = guarded {
        when {
            id == null -> emptyDraft().right()
            else -> stored[id]?.right() ?: BlogError.NotFound.left()
        }
    }

    override suspend fun save(draft: Draft): Either<BlogError, Draft> = guarded(WRITE_DELAY) {
        // The first save is what gives a post an id. Until then it exists only in
        // the browser, which is the state the design warns about on the way out.
        val saved = draft.copy(id = draft.id ?: "draft-${draft.title.slugify()}").recount()
        stored[saved.id!!] = saved
        saved.right()
    }

    override suspend fun publish(draft: Draft, replaceExisting: Boolean): Either<BlogError, PublishOutcome> =
        guarded(WRITE_DELAY) {
            val slug = draft.seo.slug.trim().ifBlank { draft.title.slugify() }
            val holder = stored.values.firstOrNull {
                it.id != draft.id && it.status == PostStatus.PUBLISHED && it.seo.slug == slug
            }
            if (holder != null && !replaceExisting) {
                return@guarded PublishOutcome.SlugTaken(
                    slug = slug,
                    heldBy = holder.title,
                    suggestion = "$slug-2",
                ).right()
            }
            if (holder != null) {
                // Replacing hands the URL over: the old post keeps its content but
                // stops being the thing at that address.
                stored[holder.id!!] = holder.copy(status = PostStatus.DRAFT)
            }
            val published = draft
                .copy(id = draft.id ?: slug, status = PostStatus.PUBLISHED, seo = draft.seo.copy(slug = slug))
                .recount()
            stored[published.id!!] = published
            PublishOutcome.Published(slug).right()
        }

    override suspend fun unpublish(id: String): Either<BlogError, Unit> = guarded(WRITE_DELAY) {
        val post = stored[id]
        if (post == null) {
            BlogError.NotFound.left()
        } else {
            stored[id] = post.copy(status = PostStatus.DRAFT)
            Unit.right()
        }
    }

    override suspend fun discard(id: String): Either<BlogError, Unit> = guarded(WRITE_DELAY) {
        if (stored.remove(id) == null) BlogError.NotFound.left() else Unit.right()
    }

    override suspend fun media(): Either<BlogError, List<MediaItem>> = guarded { uploads.right() }

    override suspend fun upload(file: UploadRequest): Either<BlogError, MediaItem> = guarded(WRITE_DELAY) {
        val item = MediaItem(file.name, "/blog/assets/sample/${file.name}")
        uploads = listOf(item) + uploads
        item.right()
    }

    private var profile = SampleContent.rahul

    override suspend fun profile(): Either<BlogError, Author> = guarded { profile.right() }

    override suspend fun saveProfile(
        name: String,
        bio: String,
        topics: String,
        since: String,
    ): Either<BlogError, Author> = guarded(WRITE_DELAY) {
        profile = profile.copy(
            name = name,
            initial = name.take(1).uppercase(),
            bio = bio,
            topics = topics,
            since = since,
        )
        profile.right()
    }

    override suspend fun analytics(): Either<BlogError, Analytics> = guarded { SampleContent.analytics.right() }

    /**
     * Every admin read and write, behind the session check.
     *
     * Here so the check cannot be forgotten on a new method — the route guard
     * keeps a reader out of the screen, but this is what keeps them out of the
     * data.
     */
    private suspend fun <T> guarded(
        wait: Long = READ_DELAY,
        block: suspend () -> Either<BlogError, T>,
    ): Either<BlogError, T> {
        if (auth.session().getOrNull() == null) return BlogError.NotSignedIn.left()
        delay(wait)
        return block()
    }
}

// ── Shared helpers ───────────────────────────────────────────────────────────

private fun emptyDraft(): Draft = Draft(
    id = null,
    title = "",
    body = emptyList(),
    status = PostStatus.DRAFT,
    seo = SeoDraft(),
    wordCount = 0,
    readingMinutes = 0,
)

private fun PostSummary.asDraft(status: PostStatus): Draft {
    val body = SampleContent.bodies[slug].orEmpty()
    return Draft(
        id = slug,
        title = title,
        body = body,
        status = status,
        seo = SeoDraft(seoTitle = title, slug = slug, metaDescription = dek, categorySlug = category.slug),
        wordCount = body.wordCount(),
        readingMinutes = readingMinutes,
    )
}

/** Recomputes what the editor's footer shows, so it can never be stale. */
private fun Draft.recount(): Draft {
    val words = body.wordCount()
    return copy(wordCount = words, readingMinutes = maxOf(1, (words + 199) / 200))
}

private fun List<ArticleBlock>.wordCount(): Int = sumOf { block ->
    when (block) {
        is ArticleBlock.Paragraph -> block.runs.sumOf { it.text.words() }
        is ArticleBlock.Callout -> block.runs.sumOf { it.text.words() }
        is ArticleBlock.Section -> block.text.words()
        is ArticleBlock.AppShowcase -> block.heading.words() + block.body.words()
        // A rule is not words; counting it would inflate the reading time.
        ArticleBlock.Divider -> 0
    }
}

private fun String.words(): Int = split(' ', '\n').count { it.isNotBlank() }

private fun String.slugify(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "untitled" }

/** Enough to catch a typo, not enough to reject a real address. */
private fun String.looksLikeEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && lastIndexOf('.') > at + 1 && !endsWith(".")
}
