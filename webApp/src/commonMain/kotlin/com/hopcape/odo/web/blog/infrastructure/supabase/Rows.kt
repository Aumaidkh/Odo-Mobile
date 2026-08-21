package com.hopcape.odo.web.blog.infrastructure.supabase

import com.hopcape.odo.web.blog.domain.ImportedPost
import com.hopcape.odo.web.blog.domain.PostImporter
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.Author
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.domain.model.TextRun
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The database's shapes, and the mapping to the app's.
 *
 * A separate set of types rather than annotations on the domain model, for the
 * same reason `:core:domain` carries none: a column rename should not reach a
 * screen, and a screen's model should not decide a column name. The cost is this
 * file; the benefit is that it is the only file either change touches.
 */

@Serializable
internal data class CategoryRow(
    val slug: String,
    val name: String,
    val blurb: String = "",
    val position: Int = 0,
) {
    fun toCategory(): Category = Category(slug = slug, name = name, blurb = blurb)
}

@Serializable
internal data class AuthorRow(
    val id: String,
    val slug: String,
    val name: String,
    val initial: String = "",
    val bio: String = "",
    val topics: String = "",
    @SerialName("since_label") val sinceLabel: String = "",
) {
    fun toAuthor(articleCount: Int = 0): Author = Author(
        slug = slug,
        name = name,
        initial = initial.ifBlank { name.take(1).uppercase() },
        bio = bio,
        articleCount = articleCount,
        topics = topics,
        since = sinceLabel,
    )
}

/**
 * One post row.
 *
 * `author` and `category` come back embedded, because PostgREST can resolve a
 * foreign key in the same request — `select=*,author:blog_authors(*)`. That keeps
 * a byline to one round trip instead of one per post, which is the difference
 * between a list page and a list page that fans out.
 */
@Serializable
internal data class PostRow(
    val id: String,
    val slug: String? = null,
    val title: String = "",
    val dek: String = "",
    @SerialName("category_slug") val categorySlug: String? = null,
    val status: String = "draft",
    val body: JsonElement = JsonArray(emptyList()),
    @SerialName("seo_title") val seoTitle: String = "",
    @SerialName("meta_description") val metaDescription: String = "",
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("reading_minutes") val readingMinutes: Int = 1,
    @SerialName("published_on") val publishedOn: String? = null,
    val views: Long = 0,
    val author: AuthorRow? = null,
    val category: CategoryRow? = null,
) {
    val postStatus: PostStatus
        get() = if (status == "published") PostStatus.PUBLISHED else PostStatus.DRAFT

    /**
     * The card shape.
     *
     * A row with no category or no date is a draft that leaked through a query
     * meant for published posts; rather than fail the whole page, it falls back to
     * values that render — the surrounding list is still useful.
     */
    fun toSummary(): PostSummary = PostSummary(
        slug = slug.orEmpty(),
        title = title,
        dek = dek,
        category = category?.toCategory() ?: Category(categorySlug.orEmpty(), categorySlug.orEmpty()),
        publishedOn = publishedOn?.let(::parseDate) ?: LocalDate(1970, 1, 1),
        readingMinutes = readingMinutes,
    )

    fun blocks(): List<ArticleBlock> = decodeBlocks(body)
}

@Serializable
internal data class MediaRow(
    val id: String,
    val name: String,
    val path: String,
    @SerialName("alt_text") val altText: String = "",
)

/** What `blog_analytics()` returns: one row, three numbers. */
@Serializable
internal data class AnalyticsRow(
    val views: Long = 0,
    @SerialName("from_search") val fromSearch: Long = 0,
    val days: Int = 30,
)

// ── The article body ─────────────────────────────────────────────────────────
//
// Stored as JSON, so it needs a shape on the wire. A `type` discriminator rather
// than kotlinx's polymorphic machinery: the JSON is read by Postgres and may one
// day be read by something that is not Kotlin at all, and a hand-written
// discriminator is the version of this that anything can parse.

@Serializable
private data class RunJson(val text: String, val bold: Boolean = false, val italic: Boolean = false)

@Serializable
private data class BlockJson(
    val type: String,
    val id: String = "",
    val text: String = "",
    val label: String = "",
    val runs: List<RunJson> = emptyList(),
    val heading: String = "",
    val body: String = "",
    val cta: String = "",
    val screenshot: String? = null,
)

private const val SECTION = "section"
private const val PARAGRAPH = "paragraph"
private const val CALLOUT = "callout"
private const val SHOWCASE = "showcase"

internal fun encodeBlocks(blocks: List<ArticleBlock>): String =
    BLOCKS.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(BlockJson.serializer()),
        blocks.map { block ->
            when (block) {
                is ArticleBlock.Section -> BlockJson(type = SECTION, id = block.id, text = block.text)
                is ArticleBlock.Paragraph -> BlockJson(type = PARAGRAPH, runs = block.runs.map { RunJson(it.text, it.bold, it.italic) })
                is ArticleBlock.Callout -> BlockJson(type = CALLOUT, label = block.label, runs = block.runs.map { RunJson(it.text, it.bold, it.italic) })
                is ArticleBlock.AppShowcase -> BlockJson(
                    type = SHOWCASE,
                    heading = block.heading,
                    body = block.body,
                    cta = block.callToAction,
                    screenshot = block.screenshot,
                )
            }
        },
    )

/**
 * Blocks back out of JSON.
 *
 * An unknown `type` is dropped rather than failing the read. A body written by a
 * newer version of the CMS should still render everything this one understands —
 * losing one block beats losing the article.
 */
internal fun decodeBlocks(element: JsonElement): List<ArticleBlock> =
    runCatching {
        BLOCKS.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(BlockJson.serializer()),
            element,
        )
    }.getOrNull().orEmpty().mapNotNull { json ->
        when (json.type) {
            SECTION -> ArticleBlock.Section(json.id.ifBlank { json.text.slugify() }, json.text)
            PARAGRAPH -> ArticleBlock.Paragraph(json.runs.map { TextRun(it.text, it.bold, it.italic) })
            CALLOUT -> ArticleBlock.Callout(json.label, json.runs.map { TextRun(it.text, it.bold, it.italic) })
            SHOWCASE -> ArticleBlock.AppShowcase(json.heading, json.body, json.cta, json.screenshot)
            else -> null
        }
    }

private val BLOCKS = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** `2026-08-18` — what Postgres sends for a `date`. */
private fun parseDate(value: String): LocalDate =
    runCatching { LocalDate.parse(value.take(10)) }.getOrElse { LocalDate(1970, 1, 1) }

private fun String.slugify(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "section" }

/**
 * Reads a pasted post, in the shape this database stores.
 *
 * Two inputs are accepted because two things get pasted: a whole post object, and
 * a bare array of blocks — which is what somebody copying one article's body will
 * have. Anything else is null, and the editor says so rather than throwing.
 */
internal class JsonPostImporter : PostImporter {

    override fun parse(json: String): ImportedPost? {
        val element = runCatching { Json.parseToJsonElement(json.trim()) }.getOrNull() ?: return null

        if (element is JsonArray) {
            val body = decodeBlocks(element)
            return if (body.isEmpty()) null else ImportedPost(null, null, null, body)
        }

        val obj = element as? JsonObject ?: return null
        val body = decodeBlocks(obj["body"] ?: JsonArray(emptyList()))
        // A post with no readable blocks is not a post. Accepting it would replace
        // whatever is in the editor with nothing, which is the one outcome an
        // import must never have.
        if (body.isEmpty()) return null

        return ImportedPost(
            title = obj.text("title") ?: obj.text("seo_title"),
            dek = obj.text("dek") ?: obj.text("meta_description"),
            slug = obj.text("slug"),
            body = body,
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
