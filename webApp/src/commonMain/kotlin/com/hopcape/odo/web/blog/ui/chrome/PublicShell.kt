package com.hopcape.odo.web.blog.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.core.platform.PLAY_LISTING
import com.hopcape.odo.web.core.config.BuildWebConfig
import com.hopcape.odo.web.core.platform.openExternal
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_brand
import com.hopcape.odo.web.blog.resources.bl_footer_note
import com.hopcape.odo.web.blog.resources.bl_footer_privacy
import com.hopcape.odo.web.blog.resources.bl_footer_terms
import com.hopcape.odo.web.blog.resources.bl_nav_blog
import com.hopcape.odo.web.blog.resources.bl_nav_get_app
import com.hopcape.odo.web.blog.resources.bl_nav_search_placeholder
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.BlogTextField
import com.hopcape.odo.web.blog.ui.component.Hairline
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/** The widest the reading column ever gets. The design's frames are 1440 wide. */
private val PAGE_MAX = 1200.dp

/**
 * The frame every public page sits in: the nav, the page, the footer.
 *
 * The header does not scroll away, because the search box and the categories in
 * it are the only navigation on the site — a reader three screens into an
 * article has nowhere else to go from.
 *
 * The search term is held here rather than by a screen. It belongs to the header,
 * which outlives any one page: typing a term and pressing enter navigates, and
 * the box keeps what was typed when the results arrive.
 */
@Composable
fun PublicShell(
    categories: List<Category>,
    activeCategorySlug: String?,
    searchTerm: String,
    onNavigate: (BlogRoute) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact

    Column(Modifier.fillMaxSize().background(colors.background)) {
        PublicHeader(categories, activeCategorySlug, searchTerm, onNavigate)
        Hairline()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = PAGE_MAX)
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 20.dp else 64.dp)
                    .padding(top = if (compact) 32.dp else 56.dp, bottom = 72.dp),
                content = content,
            )
            PublicFooter(onNavigate)
        }
    }
}

@Composable
private fun PublicHeader(
    categories: List<Category>,
    activeCategorySlug: String?,
    searchTerm: String,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact

    Box(Modifier.fillMaxWidth().background(colors.background), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = PAGE_MAX)
                .fillMaxWidth()
                .padding(horizontal = if (compact) 20.dp else 64.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Wordmark { onNavigate(BlogRoute.Public.Index) }

            if (!compact) {
                TextLink(
                    text = stringResource(Res.string.bl_nav_blog),
                    onClick = { onNavigate(BlogRoute.Public.Index) },
                    color = if (activeCategorySlug == null) colors.text else colors.dim,
                )
                categories.forEach { category ->
                    TextLink(
                        text = category.name,
                        onClick = { onNavigate(BlogRoute.Public.Category(category.slug)) },
                        color = if (activeCategorySlug == category.slug) colors.text else colors.dim,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!compact) {
                SearchBox(searchTerm, onNavigate, Modifier.widthIn(max = 220.dp))
            }
            PillButton(
                text = stringResource(Res.string.bl_nav_get_app),
                onClick = { openExternal(PLAY_LISTING) },
            )
        }
    }
}

/**
 * The wordmark, in the tracking it has everywhere else on odoapp.in.
 *
 * `.34em` is the number the legal pages and the landing page both use; it is the
 * one piece of the brand that has to match exactly, because it appears on three
 * separately-built pages a reader moves between.
 */
@Composable
private fun Wordmark(onClick: () -> Unit) {
    TextLink(
        text = stringResource(Res.string.bl_brand),
        onClick = onClick,
        color = BlogThemeTokens.colors.text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.34.em,
        ),
    )
}

/**
 * The header's search box.
 *
 * Navigates on enter rather than on every keystroke: each search is a URL, and
 * pushing one per character would fill the back button with half-typed words.
 */
@Composable
private fun SearchBox(
    searchTerm: String,
    onNavigate: (BlogRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the term in the address bar, so arriving at /search?q=challan
    // shows "challan" in the box rather than whatever was typed last.
    var typed by remember(searchTerm) { mutableStateOf(searchTerm) }
    BlogTextField(
        value = typed,
        onValueChange = { typed = it },
        placeholder = stringResource(Res.string.bl_nav_search_placeholder),
        onSubmit = { if (typed.isNotBlank()) onNavigate(BlogRoute.Public.Search(typed.trim())) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

@Composable
private fun PublicFooter(onNavigate: (BlogRoute) -> Unit) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Hairline()
        Row(
            modifier = Modifier
                .widthIn(max = PAGE_MAX)
                .fillMaxWidth()
                .padding(horizontal = if (compact) 20.dp else 64.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.bl_footer_note),
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // The legal pages are separately deployed HTML on the same domain, so
            // they are a navigation out of this app rather than a route in it.
            // Absolute, not relative. The legal pages belong to the marketing site,
            // and since the blog moved to its own subdomain a bare "/legal/privacy"
            // resolves against the blog and 404s — on the very link the Play
            // listing points at.
            TextLink(
                stringResource(Res.string.bl_footer_privacy),
                { openExternal("${BuildWebConfig.SITE_BASE_URL}/legal/privacy") },
                color = colors.muted,
            )
            TextLink(
                stringResource(Res.string.bl_footer_terms),
                { openExternal("${BuildWebConfig.SITE_BASE_URL}/legal/terms") },
                color = colors.muted,
            )
        }
    }
}
