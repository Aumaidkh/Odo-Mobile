package com.hopcape.odo.web.blog.infrastructure.supabase

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.hopcape.odo.web.blog.domain.BlogError
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.Article
import com.hopcape.odo.web.blog.domain.model.AuthorPage
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.CategoryPage
import com.hopcape.odo.web.blog.domain.model.IndexPage
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.domain.model.SearchResults

/**
 * The reader-facing blog, out of Postgres.
 *
 * Every call here runs as `anon`, and the only rows that role can see are
 * published ones — that is a policy in the schema, not a filter written here.
 * A `status=eq.published` in a query would be a second copy of the same rule, and
 * the copy is the one that drifts.
 *
 * Author and category come back embedded rather than joined afterwards.
 * PostgREST resolves a foreign key in the same request, so a grid of twelve
 * bylines is one round trip instead of thirteen.
 */
internal class SupabaseBlogRepository(
    private val postgrest: Postgrest,
) : BlogRepository {

    override suspend fun categories(): Either<BlogError, List<Category>> =
        postgrest.select("blog_categories", CategoryRow.serializer(), "order=position.asc")
            .map { rows -> rows.map { it.toCategory() } }

    override suspend fun index(): Either<BlogError, IndexPage> = either {
        val posts = postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "$EMBED&order=published_on.desc&limit=$INDEX_SIZE",
        ).bind().map { it.toSummary() }

        IndexPage(
            lead = posts.firstOrNull(),
            rest = posts.drop(1),
            categories = categories().bind(),
        )
    }

    override suspend fun article(slug: String): Either<BlogError, Article> = either {
        val row = postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "$EMBED&slug=eq.${slug.encoded()}&limit=1",
        ).bind().firstOrNull() ?: raise(BlogError.NotFound)

        val summary = row.toSummary()
        Article(
            summary = summary,
            author = row.author?.toAuthor() ?: UNKNOWN_AUTHOR,
            body = row.blocks(),
            // From other categories: somebody who finished this one is done with
            // the topic, and the next click should widen rather than repeat.
            readNext = postgrest.select(
                table = "blog_posts",
                serializer = PostRow.serializer(),
                query = "$EMBED&slug=neq.${slug.encoded()}" +
                    "&category_slug=neq.${(row.categorySlug ?: "-").encoded()}" +
                    "&order=published_on.desc&limit=2",
            ).bind().map { it.toSummary() },
        )
    }

    override suspend fun category(slug: String): Either<BlogError, CategoryPage> = either {
        val category = postgrest.select(
            table = "blog_categories",
            serializer = CategoryRow.serializer(),
            query = "slug=eq.${slug.encoded()}&limit=1",
        ).bind().firstOrNull()?.toCategory() ?: raise(BlogError.NotFound)

        CategoryPage(
            category = category,
            posts = postgrest.select(
                table = "blog_posts",
                serializer = PostRow.serializer(),
                query = "$EMBED&category_slug=eq.${slug.encoded()}&order=published_on.desc",
            ).bind().map { it.toSummary() },
        )
    }

    override suspend fun author(slug: String): Either<BlogError, AuthorPage> = either {
        val row = postgrest.select(
            table = "blog_authors",
            serializer = AuthorRow.serializer(),
            query = "slug=eq.${slug.encoded()}&limit=1",
        ).bind().firstOrNull() ?: raise(BlogError.NotFound)

        val posts = postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "$EMBED&author_id=eq.${row.id}&order=published_on.desc",
        ).bind().map { it.toSummary() }

        // The count comes from the rows actually returned rather than a column on
        // the author, which would be a number to keep in step with reality.
        AuthorPage(author = row.toAuthor(articleCount = posts.size), posts = posts)
    }

    /**
     * Full-text search over the title and the dek.
     *
     * `websearch_to_tsquery` and not `ilike`: it understands quoted phrases and
     * `-excluded`, it uses the GIN index, and it does not get slower as the corpus
     * grows. The index is on a generated column, so the client never has to know
     * how the vector is built.
     */
    override suspend fun search(query: String): Either<BlogError, SearchResults> = either {
        val term = query.trim()
        if (term.isEmpty()) return@either SearchResults(query, emptyList(), emptyList())

        val hits = postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "$EMBED&search_vector=wfts(simple).${term.encoded()}&order=published_on.desc",
        ).bind().map { it.toSummary() }

        SearchResults(
            query = term,
            hits = hits,
            // Only on a dead end. Suggestions beside results would read as more
            // results, and the design only shows them when there is nothing.
            suggestions = if (hits.isNotEmpty()) emptyList() else mostRead(SUGGESTIONS).bind(),
        )
    }

    override suspend fun mostRead(limit: Int): Either<BlogError, List<PostSummary>> =
        postgrest.select(
            table = "blog_posts",
            serializer = PostRow.serializer(),
            query = "$EMBED&order=views.desc&limit=$limit",
        ).map { rows -> rows.map { it.toSummary() } }

    override suspend fun subscribe(email: String): Either<BlogError, Unit> =
        // Upsert, so subscribing twice is not an error the reader has to see.
        postgrest.upsert(
            table = "blog_subscribers",
            body = """[{"email":"${email.trim().jsonEscaped()}"}]""",
            serializer = EmptyRow.serializer(),
            onConflict = "email",
        ).map { }

    override suspend fun requestTopic(email: String, query: String): Either<BlogError, Unit> =
        postgrest.upsert(
            table = "blog_topic_requests",
            body = """[{"email":"${email.trim().jsonEscaped()}","query":"${query.jsonEscaped()}"}]""",
            serializer = EmptyRow.serializer(),
            onConflict = "id",
        ).map { }

    /**
     * Counts a read.
     *
     * Fire and forget: a page view that fails to record is not something to tell
     * the reader about, and the article is already on screen by the time this runs.
     */
    suspend fun recordView(slug: String, fromSearch: Boolean) {
        postgrest.call(
            name = "blog_record_view",
            body = """{"p_slug":"${slug.jsonEscaped()}","p_from_search":$fromSearch}""",
        )
    }

    private companion object {
        /** One request, byline and category included. */
        const val EMBED = "select=*,author:blog_authors(*),category:blog_categories(*)"

        /** The lead story plus the grid under it. */
        const val INDEX_SIZE = 5

        const val SUGGESTIONS = 3

        val UNKNOWN_AUTHOR = com.hopcape.odo.web.blog.domain.model.Author(
            slug = "",
            name = "Odo",
            initial = "O",
        )
    }
}

/** PostgREST returns `[]` from an insert that asks for nothing back. */
@kotlinx.serialization.Serializable
internal class EmptyRow

/**
 * Percent-encodes a value going into a PostgREST query string.
 *
 * A slug is url-safe by construction, but a search term is whatever somebody
 * typed — including the `&` that would otherwise end the filter and start a new
 * parameter.
 */
internal fun String.encoded(): String = buildString {
    this@encoded.encodeToByteArray().forEach { byte ->
        val character = byte.toInt().toChar()
        if (byte >= 0 && (character.isLetterOrDigit() || character in "-_.~")) {
            append(character)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

/** Escapes a value going inside a hand-built JSON string literal. */
internal fun String.jsonEscaped(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

private const val HEX = "0123456789ABCDEF"
