package com.hopcape.odo.web.blog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.routing.BlogRoute.Admin
import com.hopcape.odo.web.blog.routing.BlogRoute.Public
import com.hopcape.odo.web.blog.routing.Router
import com.hopcape.odo.web.blog.routing.location

/**
 * The shell: one branch per page, and nothing else.
 *
 * Every branch is a placeholder today. The point of writing the `when` first is
 * that it is exhaustive — the compiler now refuses a new route that no screen
 * draws, and the UI lands one branch at a time without anybody having to
 * rediscover what the pages are.
 *
 * The frame names in the comments are the ones in the design files, so a branch
 * can be matched to what it is supposed to look like.
 */
@Composable
fun BlogApp(router: Router) {
    // Public is dark and Admin is light — deliberately, per the design: reading
    // long form on black is fine, writing it is not. That split lands with the
    // themes; for now both branches draw the same placeholder.
    when (val route = router.current) {
        // ── Public ───────────────────────────────────────────────────────────
        Public.Index -> Placeholder("Index", "INDEX — lead story, then the grid", router)
        is Public.Article -> Placeholder("Article", "ARTICLE — contents rail, 680px measure", router, "slug" to route.slug)
        is Public.Category -> Placeholder("Category", "CATEGORY — thin category gets the subscribe block", router, "category" to route.slug)
        is Public.Author -> Placeholder("Author", "AUTHOR — bio, stats, their articles", router, "author" to route.slug)
        is Public.Search -> Placeholder("Search", "SEARCH — no results gets suggestions + topic request", router, "q" to route.query)
        is Public.NotFound -> Placeholder("NotFound", "404 — most-read articles", router, "attempted" to route.attempted)

        // ── Admin ────────────────────────────────────────────────────────────
        Admin.SignIn -> Placeholder("SignIn", "LOGIN — wrong password, tries left", router)
        Admin.Posts -> Placeholder("Posts", "POSTS — all / published / drafts", router)
        is Admin.Editor -> Placeholder(
            "Editor",
            "EDITOR — publish/SEO, slug conflict and insert-image are overlays here",
            router,
            "postId" to (route.postId ?: "(new, never saved)"),
        )
        Admin.Media -> Placeholder("Media", "MEDIA — upload, then the grid", router)
        Admin.Analytics -> Placeholder("Analytics", "ANALYTICS — 30 days", router)
        Admin.Settings -> Placeholder("Settings", "no frame designed yet", router)
    }
}

/**
 * Scaffolding, and it should look like it.
 *
 * Left unstyled on purpose: this is here to prove the address bar, the back
 * button and every link agree with each other, and anything that looked
 * finished would be mistaken for the design.
 */
@Composable
private fun Placeholder(
    screen: String,
    frame: String,
    router: Router,
    vararg arguments: Pair<String, String>,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(screen, style = MaterialTheme.typography.headlineSmall)
            Text(frame, style = MaterialTheme.typography.bodySmall)
            Text(router.current.location().ifEmpty { "/" }, style = MaterialTheme.typography.bodyMedium)
            arguments.forEach { (name, value) ->
                Text("$name = $value", style = MaterialTheme.typography.bodyMedium)
            }

            Text("", style = MaterialTheme.typography.bodySmall)
            Text("go to:", style = MaterialTheme.typography.bodySmall)
            EVERY_ROUTE.forEach { route ->
                Text(
                    text = route.location().ifEmpty { "/" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { router.go(route) }.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * One of each route, for the placeholder's link list.
 *
 * Sample slugs come from the design's own examples, so clicking through lands on
 * the pages the frames were drawn for.
 */
private val EVERY_ROUTE: List<BlogRoute> = listOf(
    Public.Index,
    Public.Article("challan-kaise-check-karein"),
    Public.Category("challans"),
    Public.Author("rahul-deshmukh"),
    Public.Search("challan"),
    Public.Search(""),
    Public.NotFound("/blog/hata-diya-gaya"),
    Admin.SignIn,
    Admin.Posts,
    Admin.Editor(null),
    Admin.Editor("challan-kaise-check-karein"),
    Admin.Media,
    Admin.Analytics,
    Admin.Settings,
)
