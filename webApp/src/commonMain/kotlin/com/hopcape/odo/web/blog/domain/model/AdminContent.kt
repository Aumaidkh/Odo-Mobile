package com.hopcape.odo.web.blog.domain.model

import androidx.compose.runtime.Immutable

/** What the CMS is made of. Nothing here is ever handed to a public screen. */

/** Who is signed in. The editor stamps this on what it saves. */
@Immutable
data class Session(val authorSlug: String, val name: String, val initial: String)

/** Where a post is in its life. Draft and published are the only two states. */
enum class PostStatus { DRAFT, PUBLISHED }

/**
 * A row in the post list.
 *
 * [slug] is null while a post has never been published — the design says
 * "draft — no slug yet" — and null is the only thing that cannot be confused
 * with a slug somebody actually chose.
 */
@Immutable
data class PostRow(
    val id: String,
    val title: String,
    val slug: String?,
    val status: PostStatus,
    /** Null for a draft, which has not been read by anybody. */
    val views: Int?,
    /** "2 days ago", "Today". Computed where the clock is, not in the UI. */
    val updatedLabel: String,
)

/** The post being edited, body and all. */
@Immutable
data class Draft(
    /** Null until the first save. A post with no id has never left the browser. */
    val id: String?,
    val title: String,
    val body: List<ArticleBlock>,
    val status: PostStatus,
    val seo: SeoDraft,
    val wordCount: Int,
    val readingMinutes: Int,
)

/** The publish sheet's fields. Kept apart because they are edited as one form. */
@Immutable
data class SeoDraft(
    val seoTitle: String = "",
    val slug: String = "",
    val metaDescription: String = "",
    val categorySlug: String? = null,
) {
    companion object {
        /** What Google truncates at. The counters in the design turn red past these. */
        const val TITLE_LIMIT: Int = 60
        const val DESCRIPTION_LIMIT: Int = 160
    }
}

/**
 * The answer to "can this be published".
 *
 * A conflict is a distinct outcome rather than an error because the design
 * offers a choice — take a new slug, or replace the post that holds it — and a
 * thrown failure cannot carry which post is in the way.
 */
@Immutable
sealed interface PublishOutcome {

    data class Published(val slug: String) : PublishOutcome

    /** [heldBy] is the title of the post already at that slug. */
    @Immutable
    data class SlugTaken(val slug: String, val heldBy: String, val suggestion: String) : PublishOutcome
}

/** An uploaded image. [url] is same-origin; the CSP allows nothing else. */
@Immutable
data class MediaItem(val name: String, val url: String, val altText: String = "")

/** The analytics page. One window, no date picker in the design. */
@Immutable
data class Analytics(
    val windowLabel: String,
    val views: Int,
    /** Whole percent, 0–100. The share of views that arrived from a search engine. */
    val searchSharePercent: Int,
    val appInstalls: Int,
    val topPosts: List<TopPost>,
)

@Immutable
data class TopPost(val title: String, val views: Int)
