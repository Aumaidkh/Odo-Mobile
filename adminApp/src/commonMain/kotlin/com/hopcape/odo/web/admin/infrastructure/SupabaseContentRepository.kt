package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.admin.domain.BlogCategory
import com.hopcape.odo.web.admin.domain.BlogPost
import com.hopcape.odo.web.admin.domain.ContentRepository
import com.hopcape.odo.web.admin.domain.PostBlock
import com.hopcape.odo.web.admin.domain.PostDetail
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * `blog_posts` over PostgREST, for an admin holding `blog.write`.
 *
 * The author comes back embedded rather than as a second request — a list of
 * twenty bylines is one round trip, which is the same reason `:webApp`'s public
 * repository embeds it.
 */
internal class SupabaseContentRepository(
    private val postgrest: Postgrest,
) : ContentRepository {

    override suspend fun posts(): Either<WebError, List<BlogPost>> =
        postgrest.select(
            table = TABLE,
            serializer = PostRow.serializer(),
            // Drafts first, then most recently touched: the list exists to be
            // worked, and a published post from March is not the thing to put at
            // the top of it.
            query = "select=id,title,slug,status,views,updated_at,author:blog_authors(name)" +
                "&order=status.asc,updated_at.desc",
        ).map { rows ->
            rows.map {
                BlogPost(
                    id = it.id,
                    title = it.title.ifBlank { UNTITLED },
                    slug = it.slug,
                    authorName = it.author?.name,
                    status = it.status,
                    views = it.views,
                    updatedAt = it.updatedAt.substringBefore('T'),
                )
            }
        }

    override suspend fun detail(id: String): Either<WebError, PostDetail> =
        postgrest.select(
            table = TABLE,
            serializer = DetailRow.serializer(),
            query = "select=id,title,slug,status,views,updated_at,dek,category_slug,seo_title," +
                "meta_description,word_count,reading_minutes,body,author:blog_authors(name)" +
                "&id=eq.$id&limit=1",
        ).flatMapRight { rows ->
            val row = rows.firstOrNull() ?: return@flatMapRight WebError.NotFound.left()
            PostDetail(
                post = BlogPost(
                    id = row.id,
                    title = row.title.ifBlank { UNTITLED },
                    slug = row.slug,
                    authorName = row.author?.name,
                    status = row.status,
                    views = row.views,
                    updatedAt = row.updatedAt.substringBefore('T'),
                ),
                dek = row.dek,
                categorySlug = row.categorySlug,
                seoTitle = row.seoTitle,
                metaDescription = row.metaDescription,
                body = row.body.map(::toBlock),
                wordCount = row.wordCount,
                readingMinutes = row.readingMinutes,
            ).right()
        }

    override suspend fun saveBody(id: String, blocks: List<PostBlock>): Either<WebError, Unit> {
        val array = blocks.joinToString(",", prefix = "[", postfix = "]") { block ->
            // Untouched blocks go back exactly as they came. Rebuilding them from
            // the panel's flattened model would drop an image's URL, a table's
            // cells and every bold run in the post — silently, on the first save.
            if (!block.edited) block.raw else rebuild(block)
        }
        return postgrest.patch(
            table = TABLE,
            query = "id=eq.$id",
            body = """{"body":$array}""",
        )
    }

    override suspend fun saveMeta(
        id: String,
        title: String,
        dek: String,
        slug: String?,
        categorySlug: String?,
        seoTitle: String,
        metaDescription: String,
    ): Either<WebError, Unit> = postgrest.patch(
        table = TABLE,
        query = "id=eq.$id",
        // Nulls written explicitly, never omitted: PostgREST reads an absent key as
        // "leave this column alone", so a slug cleared in the panel would silently
        // keep its old value.
        body = """{"title":"${title.jsonEscaped()}","dek":"${dek.jsonEscaped()}",""" +
            """"slug":${slug?.let { "\"${it.jsonEscaped()}\"" } ?: "null"},""" +
            """"category_slug":${categorySlug?.let { "\"${it.jsonEscaped()}\"" } ?: "null"},""" +
            """"seo_title":"${seoTitle.jsonEscaped()}","meta_description":"${metaDescription.jsonEscaped()}"}""",
    )

    override suspend fun setPublished(id: String, published: Boolean): Either<WebError, Unit> {
        // published_on travels with the status. A post that goes live today and
        // keeps a published_on from a draft saved in March sorts wrongly on the
        // public index forever, and nothing about the row says why.
        val status = if (published) BlogPost.PUBLISHED else BlogPost.DRAFT
        val publishedOn = if (published) "\"now()\"" else "null"
        return postgrest.patch(
            table = TABLE,
            query = "id=eq.$id",
            body = """{"status":"$status","published_on":$publishedOn}""",
        )
    }

    override suspend fun delete(id: String): Either<WebError, Unit> =
        postgrest.delete(table = TABLE, query = "id=eq.$id")

    override suspend fun categories(): Either<WebError, List<BlogCategory>> =
        postgrest.select(
            table = "blog_categories",
            serializer = CategoryRow.serializer(),
            query = "select=slug,name&order=position.asc",
        ).map { rows -> rows.map { BlogCategory(it.slug, it.name) } }

    override suspend fun createDraft(
        title: String,
        dek: String,
        slug: String,
        categorySlug: String?,
    ): Either<WebError, Unit> {
        val category = categorySlug?.let { "\"${it.jsonEscaped()}\"" } ?: "null"
        return postgrest.insert(
            table = TABLE,
            // status is left to its default, which is 'draft'. Saying so explicitly
            // would invite somebody to make it a parameter, and a form that can
            // publish a post with no body is a form that eventually does.
            body = """{"title":"${title.jsonEscaped()}","dek":"${dek.jsonEscaped()}",""" +
                """"slug":"${slug.jsonEscaped()}","category_slug":$category}""",
        )
    }

    private companion object {
        const val TABLE = "blog_posts"

        /** A post saved before its title was typed. The design draws it as a state. */
        const val UNTITLED = "Untitled"
    }
}

@Serializable
private data class PostRow(
    val id: String,
    val title: String = "",
    val slug: String? = null,
    val status: String = BlogPost.DRAFT,
    val views: Long = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    val author: AuthorName? = null,
)

@Serializable
private data class AuthorName(val name: String? = null)

/** `flatMap` over the right side. Arrow has one; this avoids the import churn. */
private inline fun <A, B> Either<WebError, A>.flatMapRight(block: (A) -> Either<WebError, B>): Either<WebError, B> =
    fold({ it.left() }, block)

@Serializable
private data class CategoryRow(val slug: String, val name: String)

@Serializable
private data class DetailRow(
    val id: String,
    val title: String = "",
    val slug: String? = null,
    val status: String = BlogPost.DRAFT,
    val views: Long = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    val dek: String = "",
    @SerialName("category_slug") val categorySlug: String? = null,
    @SerialName("seo_title") val seoTitle: String = "",
    @SerialName("meta_description") val metaDescription: String = "",
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("reading_minutes") val readingMinutes: Int = 1,
    /**
     * The raw array, not decoded blocks.
     *
     * Each element is parsed twice: once into [BlockJson] for what the panel shows,
     * and once kept verbatim as its own JSON so an untouched block can be written
     * back exactly as it was stored.
     */
    val body: List<JsonObject> = emptyList(),
    val author: AuthorName? = null,
)

/**
 * The body as it is stored.
 *
 * A mirror of `:webApp`'s own wire shape rather than a shared type: the two modules
 * do not depend on each other, and the discriminator is deliberately hand-written
 * so anything can read it. Every field defaults, so a block type this panel has
 * never heard of decodes rather than failing the whole post.
 */
@Serializable
private data class BlockJson(
    val type: String = "",
    val text: String = "",
    val label: String = "",
    val runs: List<RunJson> = emptyList(),
    val heading: String = "",
    val body: String = "",
    val items: List<List<RunJson>> = emptyList(),
    val alt: String = "",
    val caption: String = "",
    val rows: List<List<String>> = emptyList(),
)

@Serializable
private data class RunJson(val text: String = "")

/**
 * Flattens one stored block to the panel's read-only shape.
 *
 * Styled runs are joined into plain text: the preview exists so somebody can read
 * what a post says before publishing it, and bold inside a sentence is not what
 * they are checking for. An unknown type keeps its words rather than vanishing —
 * a preview that silently drops a block would be a preview that lies.
 */
/**
 * One edited block, back in the stored shape.
 *
 * Runs collapse to a single unstyled run, which is the flattening [PostBlock.raw]
 * exists to keep off everything else. Bullets split on newlines, because that is how
 * the panel presents them and a bullet list is the one text block whose structure is
 * visible in a plain text box.
 */
private fun rebuild(block: PostBlock): String = when (block.kind) {
    PostBlock.Kind.Section ->
        """{"type":"section","id":"${block.text.slugified().jsonEscaped()}","text":"${block.text.jsonEscaped()}"}"""

    PostBlock.Kind.Paragraph ->
        """{"type":"paragraph","runs":[{"text":"${block.text.jsonEscaped()}"}]}"""

    PostBlock.Kind.Callout ->
        """{"type":"callout","label":"${block.label.jsonEscaped()}","runs":[{"text":"${block.text.jsonEscaped()}"}]}"""

    PostBlock.Kind.Bullets -> {
        val items = block.text.split('\n')
            .map { it.trim().removePrefix("•").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",") { """[{"text":"${it.jsonEscaped()}"}]""" }
        """{"type":"bullets","items":[$items]}"""
    }

    PostBlock.Kind.Divider -> """{"type":"divider"}"""

    // Not editable in this panel, so `edited` is never set on one and this is
    // unreachable — kept total rather than throwing, because an exhaustive `when`
    // that cannot lose data is worth more than a branch nobody reaches.
    else -> block.raw
}

/**
 * The anchor a section heading gets.
 *
 * Matches the blog's own rule — lowercase, non-alphanumerics to hyphens — so an
 * edited heading keeps working as a contents link rather than pointing at nothing.
 */
private fun String.slugified(): String =
    lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        .split('-').filter { it.isNotEmpty() }.joinToString("-")
        .ifEmpty { "section" }

private fun toBlock(element: JsonObject): PostBlock {
    val json = runCatching { BLOCKS.decodeFromJsonElement(BlockJson.serializer(), element) }
        .getOrElse { BlockJson() }
    return flatten(json).copy(raw = element.toString())
}

/** Ignores block types this panel has never heard of rather than failing the post. */
private val BLOCKS = Json { ignoreUnknownKeys = true }

private fun flatten(json: BlockJson): PostBlock = when (json.type) {
    "section" -> PostBlock(PostBlock.Kind.Section, json.text)
    "paragraph" -> PostBlock(PostBlock.Kind.Paragraph, json.runs.joinToString("") { it.text })
    "callout" -> PostBlock(PostBlock.Kind.Callout, json.runs.joinToString("") { it.text }, json.label)
    "bullets" -> PostBlock(
        PostBlock.Kind.Bullets,
        json.items.joinToString("\n") { item -> "• " + item.joinToString("") { it.text } },
    )
    "divider" -> PostBlock(PostBlock.Kind.Divider, "")
    "image" -> PostBlock(PostBlock.Kind.Image, json.caption.ifBlank { json.alt })
    "table" -> PostBlock(
        PostBlock.Kind.Table,
        json.rows.joinToString("\n") { it.joinToString("  ·  ") },
    )
    "showcase" -> PostBlock(PostBlock.Kind.Showcase, json.body, json.heading)
    else -> PostBlock(PostBlock.Kind.Unknown, json.text.ifBlank { json.runs.joinToString("") { it.text } }, json.type)
}
