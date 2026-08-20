package com.hopcape.odo.web.blog.routing

/**
 * Every page the blog has, as a type.
 *
 * A route is not a URL. The URL is what the browser holds and what a reader
 * shares; this is what the app switches on. [Routes] is the only place the two
 * are converted into each other, so a path shape can change in one file without
 * any screen knowing.
 *
 * Two trees under one root, because they are one deployment and one bundle but
 * not one audience: [Public] is what search traffic lands on, [Admin] is the
 * tool the posts are written in. The split is the thing an auth check will hang
 * off later — everything under [Admin] needs a signed-in author, and nothing
 * under [Public] ever does.
 */
sealed interface BlogRoute {

    /** The reader-facing blog. Dark, and the only part a crawler ever sees. */
    sealed interface Public : BlogRoute {

        /** `/blog` — the lead story and the grid under it. */
        data object Index : Public

        /** `/blog/<slug>` — one article. */
        data class Article(val slug: String) : Public

        /** `/blog/category/<slug>` — every article filed under one category. */
        data class Category(val slug: String) : Public

        /** `/blog/author/<slug>` — one author's bio and their articles. */
        data class Author(val slug: String) : Public

        /**
         * `/blog/search?q=…` — results for [query], which may be blank when the
         * reader opened the search page without typing anything yet.
         */
        data class Search(val query: String) : Public

        /**
         * Nothing matched.
         *
         * Carries the path that was tried so the URL can stay as the reader typed
         * it. Rewriting it to `/blog` would lose the evidence of what broke, and
         * the design's 404 is a real page with somewhere to go, not a redirect.
         */
        data class NotFound(val attempted: String) : Public
    }

    /** The CMS. Light, signed-in, and not linked from anywhere public. */
    sealed interface Admin : BlogRoute {

        /** `/blog/admin` — sign in. The only Admin route reachable signed-out. */
        data object SignIn : Admin

        /** `/blog/admin/posts` — the post list, filtered to all, published or drafts. */
        data object Posts : Admin

        /**
         * `/blog/admin/posts/new` or `/blog/admin/posts/<id>` — the editor.
         *
         * [postId] is null for a post that has never been saved. The design draws
         * that as its own state ("New post · not saved"), and it is the state
         * where leaving the page loses work, so it is worth being a distinct
         * value rather than an empty string.
         */
        data class Editor(val postId: String?) : Admin

        /** `/blog/admin/media` — uploaded screenshots. */
        data object Media : Admin

        /** `/blog/admin/analytics` — views, search share and installs. */
        data object Analytics : Admin

        /** `/blog/admin/settings` — in the nav; no screen designed for it yet. */
        data object Settings : Admin
    }
}
