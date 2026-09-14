package com.hopcape.odo.web.blog.data

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.Article
import com.hopcape.odo.web.blog.domain.model.AuthorPage
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.CategoryPage
import com.hopcape.odo.web.blog.domain.model.IndexPage
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.domain.model.SearchResults

/**
 * Remembers what the blog already fetched, for as long as the page is open.
 *
 * A reader moves index → article → back → another article, and every one of those
 * used to be a round trip for pages that had not changed in between. The index and
 * the category list are fetched by nearly every screen, so they were the worst of it.
 *
 * **Only successes are kept.** Caching a failure would pin one flaky request to the
 * whole session, and the retry the error screen offers would return the same error
 * without ever asking again.
 *
 * **In memory, so a reload refetches.** That is the staleness boundary on purpose:
 * a published article is served as static HTML anyway, this cache only shortens
 * moving around inside the app, and "reload to see today's post" is a rule readers
 * already understand. Persisting to storage would mean deciding when a cached
 * article goes stale, which is a bigger decision than this one.
 *
 * [search] is deliberately not cached: the set of queries has no bound, and results
 * that lag behind what was typed are worse than a request.
 */
class CachingBlogRepository(
    private val delegate: BlogRepository,
) : BlogRepository {

    private var categories: List<Category>? = null
    private var index: IndexPage? = null
    private val articles = mutableMapOf<String, Article>()
    private val categoryPages = mutableMapOf<String, CategoryPage>()
    private val authors = mutableMapOf<String, AuthorPage>()
    private val mostRead = mutableMapOf<Int, List<PostSummary>>()

    override suspend fun categories(): Either<WebError, List<Category>> =
        categories?.let { Either.Right(it) } ?: delegate.categories().onRight { categories = it }

    override suspend fun index(): Either<WebError, IndexPage> =
        index?.let { Either.Right(it) } ?: delegate.index().onRight { index = it }

    override suspend fun article(slug: String): Either<WebError, Article> =
        articles[slug]?.let { Either.Right(it) } ?: delegate.article(slug).onRight { articles[slug] = it }

    override suspend fun category(slug: String): Either<WebError, CategoryPage> =
        categoryPages[slug]?.let { Either.Right(it) }
            ?: delegate.category(slug).onRight { categoryPages[slug] = it }

    override suspend fun author(slug: String): Either<WebError, AuthorPage> =
        authors[slug]?.let { Either.Right(it) } ?: delegate.author(slug).onRight { authors[slug] = it }

    override suspend fun search(query: String): Either<WebError, SearchResults> = delegate.search(query)

    override suspend fun mostRead(limit: Int): Either<WebError, List<PostSummary>> =
        mostRead[limit]?.let { Either.Right(it) } ?: delegate.mostRead(limit).onRight { mostRead[limit] = it }

    // Writes, not reads. Nothing to remember, and remembering would mean a second
    // subscribe silently doing nothing.
    override suspend fun subscribe(email: String): Either<WebError, Unit> = delegate.subscribe(email)

    override suspend fun requestTopic(email: String, query: String): Either<WebError, Unit> =
        delegate.requestTopic(email, query)
}
