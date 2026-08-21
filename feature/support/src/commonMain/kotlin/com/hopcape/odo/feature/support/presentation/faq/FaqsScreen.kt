package com.hopcape.odo.feature.support.presentation.faq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoExpandableRow
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_faq_expand
import com.hopcape.odo.feature.support.resources.sp_faq_title
import org.jetbrains.compose.resources.stringResource

/**
 * The FAQ list: every question, with one answer open at a time.
 *
 * One at a time rather than many, because the answers are long enough that two open at once
 * pushes the rest off screen and the list stops looking like a list. Tapping the open row
 * closes it, so there is always a way back to the full set of questions.
 *
 * The list is static and offline. It ships with the app rather than being fetched, which is
 * what lets it answer "does Odo work without internet" while the phone has no internet.
 */
@Composable
internal fun FaqsScreen(onBack: () -> Unit) {
    val faqs = rememberResolvedFaqs()
    // Survives rotation: an owner reading a long answer should not lose it to a screen turn.
    // Keyed by id rather than index so it still points at the same question if the list
    // is ever reordered.
    var openId: String? by rememberSaveable { mutableStateOf(null) }

    OdoScreen(title = stringResource(Res.string.sp_faq_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            items(faqs, key = { it.id }) { faq ->
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

/** One question with its answer underneath, shared by the list and the search results. */
@Composable
internal fun FaqRow(
    faq: ResolvedFaq,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    OdoExpandableRow(
        title = faq.question,
        expanded = expanded,
        onToggle = onToggle,
        toggleContentDescription = stringResource(Res.string.sp_faq_expand),
    ) {
        OdoText(
            faq.answer,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
    }
}
