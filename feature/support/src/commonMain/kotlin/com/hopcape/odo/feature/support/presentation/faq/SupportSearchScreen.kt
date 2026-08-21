package com.hopcape.odo.feature.support.presentation.faq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcMagnifier
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_search_clear
import com.hopcape.odo.feature.support.resources.sp_search_empty
import com.hopcape.odo.feature.support.resources.sp_search_empty_message
import com.hopcape.odo.feature.support.resources.sp_search_hint
import com.hopcape.odo.feature.support.resources.sp_search_prompt
import com.hopcape.odo.feature.support.resources.sp_search_prompt_message
import com.hopcape.odo.feature.support.resources.sp_search_title
import org.jetbrains.compose.resources.stringResource

/**
 * Search over the same answers the FAQ list shows.
 *
 * Matching runs over the answer text as well as the question, so "background" finds the
 * permission answer even though the word is not in its title. Substring rather than whole
 * word, for the same reason the car-model sheet does it: someone typing "insur" should not
 * have to finish the word.
 *
 * There is no separate index and nothing is fetched. The catalog is a dozen entries, so
 * filtering it on each keystroke costs nothing worth optimising.
 */
@Composable
internal fun SupportSearchScreen(onBack: () -> Unit) {
    val faqs = rememberResolvedFaqs()
    var query by rememberSaveable { mutableStateOf("") }
    var openId: String? by rememberSaveable { mutableStateOf(null) }

    val trimmed = query.trim()
    val results = remember(trimmed, faqs) { faqs.matching(trimmed) }

    OdoScreen(title = stringResource(Res.string.sp_search_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.sp_search_hint),
                singleLine = true,
                leadingIcon = {
                    OdoIcon(
                        IcMagnifier,
                        contentDescription = null,
                        tint = OdoTheme.colors.textDim,
                        size = OdoTheme.iconSizes.medium,
                    )
                },
                // Only once there is something to clear. An always-present clear button on an
                // empty field is a control that does nothing.
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        OdoIconButton(
                            IcClose,
                            contentDescription = stringResource(Res.string.sp_search_clear),
                            onClick = { query = "" },
                            tint = OdoTheme.colors.textDim,
                            size = OdoTheme.iconSizes.medium,
                        )
                    }
                },
            )

            when {
                // Before anything is typed the screen explains itself rather than showing
                // every question — the full list is one back-tap away on the FAQ screen.
                trimmed.isEmpty() -> CentredState(
                    title = stringResource(Res.string.sp_search_prompt),
                    message = stringResource(Res.string.sp_search_prompt_message),
                )

                results.isEmpty() -> CentredState(
                    title = stringResource(Res.string.sp_search_empty),
                    message = stringResource(Res.string.sp_search_empty_message, trimmed),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = OdoTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    items(results, key = { it.id }) { faq ->
                        OdoCard {
                            FaqRow(
                                faq = faq,
                                expanded = faq.id == openId,
                                onToggle = { openId = if (faq.id == openId) null else faq.id },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The prompt and the no-results state, which differ only by their words. */
@Composable
private fun CentredState(title: String, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OdoEmptyState(
            title = title,
            message = message,
            icon = {
                OdoIcon(
                    IcMagnifier,
                    contentDescription = null,
                    tint = OdoTheme.colors.textMuted,
                    size = OdoTheme.iconSizes.large,
                )
            },
        )
    }
}
