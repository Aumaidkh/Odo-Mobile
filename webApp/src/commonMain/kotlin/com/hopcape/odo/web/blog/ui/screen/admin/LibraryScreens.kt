package com.hopcape.odo.web.blog.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.Analytics
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.platform.pickImage
import com.hopcape.odo.web.blog.presentation.admin.library.AnalyticsEvent
import com.hopcape.odo.web.blog.presentation.admin.library.MediaEvent
import com.hopcape.odo.web.blog.presentation.admin.settings.SettingsEvent
import com.hopcape.odo.web.blog.presentation.admin.settings.SettingsUiState
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.Submission
import com.hopcape.odo.web.blog.presentation.state.resolve
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_analytics_from_search
import com.hopcape.odo.web.blog.resources.bl_analytics_installs
import com.hopcape.odo.web.blog.resources.bl_analytics_percent
import com.hopcape.odo.web.blog.resources.bl_analytics_title
import com.hopcape.odo.web.blog.resources.bl_analytics_top_posts
import com.hopcape.odo.web.blog.resources.bl_analytics_views
import com.hopcape.odo.web.blog.resources.bl_media_drop
import com.hopcape.odo.web.blog.resources.bl_media_empty
import com.hopcape.odo.web.blog.resources.bl_media_limits
import com.hopcape.odo.web.blog.resources.bl_media_title
import com.hopcape.odo.web.blog.resources.bl_media_upload
import com.hopcape.odo.web.blog.resources.bl_media_uploading
import com.hopcape.odo.web.blog.resources.bl_settings_bio
import com.hopcape.odo.web.blog.resources.bl_settings_byline_dek
import com.hopcape.odo.web.blog.resources.bl_settings_byline_heading
import com.hopcape.odo.web.blog.resources.bl_settings_name
import com.hopcape.odo.web.blog.resources.bl_settings_save
import com.hopcape.odo.web.blog.resources.bl_settings_saved
import com.hopcape.odo.web.blog.resources.bl_settings_since
import com.hopcape.odo.web.blog.resources.bl_settings_since_hint
import com.hopcape.odo.web.blog.resources.bl_settings_title
import com.hopcape.odo.web.blog.resources.bl_settings_topics
import com.hopcape.odo.web.blog.resources.bl_settings_topics_hint
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.Hairline
import com.hopcape.odo.web.blog.ui.component.InitialAvatar
import com.hopcape.odo.web.blog.ui.component.LabelledField
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The three simple corners of the CMS: the media library, the numbers, and the
 * settings page that has nothing on it yet.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaScreen(
    items: Loadable<List<MediaItem>>,
    uploading: Boolean,
    onEvent: (MediaEvent) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.bl_media_title),
                color = colors.text,
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.weight(1f))
            PillButton(
                text = if (uploading) {
                    stringResource(Res.string.bl_media_uploading)
                } else {
                    stringResource(Res.string.bl_media_upload)
                },
                // The browser's own picker. Everything about reading the file is
                // in the platform layer; this only hands back what came out.
                onClick = { pickImage { file -> onEvent(MediaEvent.Upload(file)) } },
                enabled = !uploading,
            )
        }

        // The dropzone is a target for the eye, not for a drag: wiring HTML5 drag
        // and drop into a canvas needs DOM listeners that reach past Compose, and
        // the button next to it does the same job today.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceRaised)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .padding(vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(Res.string.bl_media_drop),
                color = colors.dim,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.bl_media_limits),
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LoadableBox(items, onRetry = { onEvent(MediaEvent.Retry) }) { list ->
            if (list.isEmpty()) {
                Text(
                    text = stringResource(Res.string.bl_media_empty),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    list.forEach { item -> MediaTile(item) }
                }
            }
        }
    }
}

/**
 * One uploaded file.
 *
 * A grey block, not the image. Drawing a bitmap on this canvas needs an image
 * loader and a network fetcher; the tile does its job — telling an author what is
 * in the library and what it is called — without one.
 */
@Composable
private fun MediaTile(item: MediaItem) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier.width(180.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceRaised)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
        )
        Text(
            text = item.name,
            color = colors.dim,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AnalyticsScreen(
    state: Loadable<Analytics>,
    onEvent: (AnalyticsEvent) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        LoadableBox(state, onRetry = { onEvent(AnalyticsEvent.Retry) }) { analytics ->
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(Res.string.bl_analytics_title),
                        color = colors.text,
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = analytics.windowLabel,
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Stat(stringResource(Res.string.bl_analytics_views), analytics.views.grouped(), Modifier.weight(1f))
                    Stat(
                        label = stringResource(Res.string.bl_analytics_from_search),
                        value = stringResource(Res.string.bl_analytics_percent, analytics.searchSharePercent),
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        label = stringResource(Res.string.bl_analytics_installs),
                        // A dash, not a zero: nothing here knows about installs.
                        value = analytics.appInstalls?.grouped() ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow(stringResource(Res.string.bl_analytics_top_posts))
                    Spacer(Modifier.height(8.dp))
                    analytics.topPosts.forEach { post ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = post.title,
                                color = colors.text,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = post.views.grouped(),
                                color = colors.dim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Eyebrow(label)
        Text(value, color = colors.text, style = MaterialTheme.typography.displayMedium)
    }
}

/**
 * The author's byline, and the only thing on this page.
 *
 * A CMS settings page usually collects whatever has nowhere else to live. There
 * is exactly one setting here that changes what a reader sees, so it is the page:
 * until an author fills this in, `blog-session` has guessed their name from the
 * local part of an email address and the author page says so.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    val colors = BlogThemeTokens.colors

    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Text(
            text = stringResource(Res.string.bl_settings_title),
            color = colors.text,
            style = MaterialTheme.typography.displayMedium,
        )

        LoadableBox(state.loaded, onRetry = { onEvent(SettingsEvent.Retry) }) { author ->
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialAvatar(state.name.take(1).uppercase().ifBlank { "?" }, diameter = 44)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = stringResource(Res.string.bl_settings_byline_heading),
                            color = colors.text,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = stringResource(Res.string.bl_settings_byline_dek),
                            color = colors.muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                LabelledField(
                    label = stringResource(Res.string.bl_settings_name),
                    value = state.name,
                    onValueChange = { onEvent(SettingsEvent.NameChanged(it)) },
                )
                LabelledField(
                    label = stringResource(Res.string.bl_settings_bio),
                    value = state.bio,
                    onValueChange = { onEvent(SettingsEvent.BioChanged(it)) },
                )
                LabelledField(
                    label = stringResource(Res.string.bl_settings_topics),
                    value = state.topics,
                    onValueChange = { onEvent(SettingsEvent.TopicsChanged(it)) },
                    trailingLabel = stringResource(Res.string.bl_settings_topics_hint),
                )
                LabelledField(
                    label = stringResource(Res.string.bl_settings_since),
                    value = state.since,
                    onValueChange = { onEvent(SettingsEvent.SinceChanged(it)) },
                    trailingLabel = stringResource(Res.string.bl_settings_since_hint),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        text = stringResource(Res.string.bl_settings_save),
                        onClick = { onEvent(SettingsEvent.Save) },
                        enabled = state.canSave,
                    )
                    when (val saving = state.saving) {
                        Submission.Done -> Text(
                            text = stringResource(Res.string.bl_settings_saved),
                            color = colors.success,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        is Submission.Failed -> Text(
                            text = saving.message.resolve(),
                            color = colors.danger,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        else -> Unit
                    }
                }
            }
        }
    }
}

/** Thousands separators. Five figures unspaced is unreadable at this size. */
private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
