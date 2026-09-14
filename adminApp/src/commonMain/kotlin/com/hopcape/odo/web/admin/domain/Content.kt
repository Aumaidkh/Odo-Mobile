package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.config.BuildWebConfig
import com.hopcape.odo.web.core.domain.WebError

/** One blog post, as the admin list shows it. */
data class BlogPost(
    val id: String,
    val title: String,
    val slug: String?,
    val authorName: String?,
    val status: String,
    val views: Long,
    val updatedAt: String,
) {
    val isPublished: Boolean get() = status == PUBLISHED

    /** A draft has never had a URL, which is a real state and not missing data. */
    val hasSlug: Boolean get() = !slug.isNullOrBlank()

    /**
     * Where this post is read, or null for a draft.
     *
     * Null rather than a guess: a draft has no slug, so there is no public URL to
     * open, and an "open" that lands on a 404 is worse than one that is not there.
     */
    val publicUrl: String? get() = slug?.takeIf { it.isNotBlank() }?.let { "${BuildWebConfig.BLOG_BASE_URL}/$it" }

    companion object {
        const val PUBLISHED = "published"
        const val DRAFT = "draft"
    }
}

/**
 * One block of a post's body, flattened to text for the panel's preview.
 *
 * Deliberately not the blog's own `ArticleBlock`. That model carries styled runs,
 * images, tables and app-promo cards, and rendering it faithfully is the blog's
 * renderer — which lives in `:webApp` along with the 3,600-line editor that writes
 * it. This is enough to *read* a post: what kind of block, and the words in it.
 * Anything richer belongs to #370.
 */
data class PostBlock(
    val kind: Kind,
    val text: String,
    val label: String = "",
    /**
     * The block exactly as it is stored.
     *
     * Carried so a save can put back what it could not represent. This model
     * flattens styled runs to plain text, drops an image's URL and a table's cells —
     * everything a preview does not need. Rebuilding every block from this model on
     * save would therefore delete all of that, quietly, the first time somebody
     * fixed a typo in the first paragraph.
     *
     * So an untouched block is written back byte for byte from here, and only the
     * ones somebody actually edited are rebuilt.
     */
    val raw: String = "",
    /** True once somebody has changed [text] in the panel. */
    val edited: Boolean = false,
) {
    enum class Kind { Section, Paragraph, Callout, Bullets, Divider, Image, Table, Showcase, Unknown }

    /**
     * Whether this panel can edit the words in it.
     *
     * The text kinds only. An image is a URL and a table is a grid of cells; both
     * are editable in the blog's own editor, and a text box that claimed to edit
     * either would be lying about what it was going to save.
     */
    val isTextual: Boolean
        get() = kind == Kind.Section || kind == Kind.Paragraph || kind == Kind.Callout || kind == Kind.Bullets

    /**
     * True when the stored block carries formatting this model cannot hold.
     *
     * Editing such a block flattens it — the bold comes out. Worth saying before
     * somebody types rather than after they save.
     */
    val hasRichRuns: Boolean
        get() = isTextual && (raw.contains("\"bold\":true") || raw.contains("\"italic\":true"))
}

/** A post with its body, for the panel's own preview. */
data class PostDetail(
    val post: BlogPost,
    val dek: String,
    val categorySlug: String?,
    val seoTitle: String,
    val metaDescription: String,
    val body: List<PostBlock>,
    val wordCount: Int,
    val readingMinutes: Int,
)

/**
 * The blog, from the admin panel.
 *
 * List, read, edit, publish, delete. The one thing it does not do is author the
 * block kinds it cannot represent — an image's URL, a table's cells — which survive
 * a save untouched rather than being rewritten from a model that never held them.
 */
interface ContentRepository {

    suspend fun posts(): Either<WebError, List<BlogPost>>

    /**
     * Publish or unpublish.
     *
     * Unpublish rather than delete for anything that has been live: the URL may
     * have been shared, and taking it to a 404 is the thing unpublishing exists
     * to avoid.
     */
    suspend fun setPublished(id: String, published: Boolean): Either<WebError, Unit>

    /** Only ever offered for a draft, which has never been anywhere. */
    suspend fun delete(id: String): Either<WebError, Unit>

    /** The categories a new post can be filed under. */
    suspend fun categories(): Either<WebError, List<BlogCategory>>

    /** One post, with its body, for the panel's preview. */
    suspend fun detail(id: String): Either<WebError, PostDetail>

    /**
     * The words of a post, block by block.
     *
     * Blocks whose `edited` flag is false are written back from their `raw` form, so
     * a save never costs an untouched image, table or bold run.
     */
    suspend fun saveBody(id: String, blocks: List<PostBlock>): Either<WebError, Unit>

    /** The parts of a post that are not its body. */
    suspend fun saveMeta(
        id: String,
        title: String,
        dek: String,
        slug: String?,
        categorySlug: String?,
        seoTitle: String,
        metaDescription: String,
    ): Either<WebError, Unit>

    /**
     * Creates a draft.
     *
     * A draft rather than a published post, always: this form collects a title, a
     * dek and a category, and a post with no body has no business being live. The
     * body is written in the editor, which is still `/blog/admin` until #370.
     */
    suspend fun createDraft(
        title: String,
        dek: String,
        slug: String,
        categorySlug: String?,
    ): Either<WebError, Unit>
}

/** One category, for the create form's picker. */
data class BlogCategory(val slug: String, val name: String)
