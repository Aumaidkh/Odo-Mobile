package com.hopcape.odo.web.blog.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * What the reader-facing blog is made of.
 *
 * These are the shapes a repository returns, and they are deliberately the
 * shapes a *page* needs rather than the rows a database will hold. A post row
 * will one day come back from PostgREST with an author id on it; resolving that
 * id to [Author] is the repository's job, not the screen's, so the screen never
 * has to make a second call to render a byline.
 */

/** A category, as it appears in the nav, on a chip and at the top of its own page. */
@Immutable
data class Category(
    /** URL segment: `/blog/category/<slug>`. */
    val slug: String,
    /** "Challans", "Service costs". */
    val name: String,
    /** The line under the category's own heading. Empty elsewhere. */
    val blurb: String = "",
)

/** An author, for a byline and for their own page. */
@Immutable
data class Author(
    val slug: String,
    val name: String,
    /** What the avatar circle shows until there is a photo to show instead. */
    val initial: String,
    val bio: String = "",
    val articleCount: Int = 0,
    /** "Challans · Costs · Resale" — what they write about. */
    val topics: String = "",
    /** "March 2025". Text, not a date: it is a label, and nothing computes on it. */
    val since: String = "",
)

/**
 * A post as it appears in a list: every card in the design, and nothing more.
 *
 * Separate from [Article] because a list of twelve posts should not carry twelve
 * article bodies. The split is also what a real query will want — a list reads
 * the summary columns, a page reads the row.
 */
@Immutable
data class PostSummary(
    val slug: String,
    val title: String,
    /** The sentence under the title. */
    val dek: String,
    val category: Category,
    val publishedOn: LocalDate,
    val readingMinutes: Int,
)

/**
 * A run of text in a paragraph.
 *
 * Both flags on one run rather than a span tree, because the output is an
 * `AnnotatedString` — flat by construction — and prose does use both at once.
 */
@Immutable
data class TextRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/**
 * One block of an article body.
 *
 * A closed set, and small on purpose: every member here is drawn somewhere in
 * [com.hopcape.odo.web.blog.ui]. A block the renderer cannot draw would reach a
 * reader as nothing at all, so the editor must not be able to produce one.
 */
@Immutable
sealed interface ArticleBlock {

    /**
     * A section heading, and an anchor in the contents rail.
     *
     * [id] is what the rail scrolls to, so it has to survive an edit to the
     * heading text — which is why it is stored rather than derived from [text].
     */
    @Immutable
    data class Section(val id: String, val text: String) : ArticleBlock

    @Immutable
    data class Paragraph(val runs: List<TextRun>) : ArticleBlock

    /** The boxed aside. [label] is its eyebrow — "WORTH KNOWING" and the like. */
    @Immutable
    data class Callout(val label: String, val runs: List<TextRun>) : ArticleBlock

    /**
     * A horizontal rule — a break between two stretches of an article that are not
     * different enough to earn a heading.
     *
     * It carries nothing, which is the point: there is no text, no id and nothing to
     * edit, so it is the one block whose editor is a line and a remove link.
     */
    data object Divider : ArticleBlock

    /**
     * A bulleted list. Each item carries its own runs, so **bold** and *italic* work
     * inside a bullet exactly as they do in a paragraph.
     *
     * One item per line in the editor, which is what makes it feel like typing a list
     * rather than filling in a form.
     */
    data class BulletList(val items: List<List<TextRun>>) : ArticleBlock

    /**
     * The app, shown inside the answer.
     *
     * The one piece of promotion inside an article, and it appears after the
     * question has already been answered by the prose around it. [screenshot] is
     * a media path, null while the design is drawn rather than photographed.
     *
     * [link] is where the button goes. It is per-card rather than fixed to the
     * Play listing because an article about insurance renewals and an article
     * about fuel costs want to land a reader in different places, and one day one
     * of them will want to land somewhere that is not a store page at all. Blank
     * means the Play listing, which is the right answer often enough to be the
     * default but not often enough to be the only option.
     */
    @Immutable
    data class AppShowcase(
        val heading: String,
        val body: String,
        val callToAction: String,
        val screenshot: String? = null,
        val link: String = "",
    ) : ArticleBlock
}

/** A post's own page: the summary, who wrote it, the body, and where to go next. */
@Immutable
data class Article(
    val summary: PostSummary,
    val author: Author,
    val body: List<ArticleBlock>,
    /** The two cards under "Read next". */
    val readNext: List<PostSummary>,
) {
    /**
     * The contents rail, derived rather than stored.
     *
     * A rail that could disagree with the body is a rail that scrolls to a
     * heading that is not there, so there is only one list and this reads it.
     */
    val contents: List<ArticleBlock.Section>
        get() = body.filterIsInstance<ArticleBlock.Section>()
}

/** Everything the index page shows. */
@Immutable
data class IndexPage(
    /** The one story at the top. Null only if nothing is published at all. */
    val lead: PostSummary?,
    val rest: List<PostSummary>,
    /** The filter chips, in the order they are shown. "All" is not one of these. */
    val categories: List<Category>,
)

/** A category's own page. [posts] may be short, which is a designed state. */
@Immutable
data class CategoryPage(val category: Category, val posts: List<PostSummary>)

/** An author's own page. */
@Immutable
data class AuthorPage(val author: Author, val posts: List<PostSummary>)

/**
 * A search result set.
 *
 * [suggestions] is what to show when [hits] is empty — the design does not leave
 * a reader on a dead end, so the repository decides what is close enough to
 * offer rather than the screen guessing.
 */
@Immutable
data class SearchResults(
    val query: String,
    val hits: List<PostSummary>,
    val suggestions: List<PostSummary>,
)
