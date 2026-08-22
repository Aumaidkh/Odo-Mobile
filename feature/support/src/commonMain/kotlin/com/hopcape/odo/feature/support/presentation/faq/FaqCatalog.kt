package com.hopcape.odo.feature.support.presentation.faq

import androidx.compose.runtime.Composable
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_faq_a_auto_odo
import com.hopcape.odo.feature.support.resources.sp_faq_a_background
import com.hopcape.odo.feature.support.resources.sp_faq_a_car_count
import com.hopcape.odo.feature.support.resources.sp_faq_a_delete
import com.hopcape.odo.feature.support.resources.sp_faq_a_fairness
import com.hopcape.odo.feature.support.resources.sp_faq_a_health
import com.hopcape.odo.feature.support.resources.sp_faq_a_no_benchmark
import com.hopcape.odo.feature.support.resources.sp_faq_a_offline
import com.hopcape.odo.feature.support.resources.sp_faq_a_pro
import com.hopcape.odo.feature.support.resources.sp_faq_a_reminders
import com.hopcape.odo.feature.support.resources.sp_faq_a_routes
import com.hopcape.odo.feature.support.resources.sp_faq_a_scan
import com.hopcape.odo.feature.support.resources.sp_faq_q_auto_odo
import com.hopcape.odo.feature.support.resources.sp_faq_q_background
import com.hopcape.odo.feature.support.resources.sp_faq_q_car_count
import com.hopcape.odo.feature.support.resources.sp_faq_q_delete
import com.hopcape.odo.feature.support.resources.sp_faq_q_fairness
import com.hopcape.odo.feature.support.resources.sp_faq_q_health
import com.hopcape.odo.feature.support.resources.sp_faq_q_no_benchmark
import com.hopcape.odo.feature.support.resources.sp_faq_q_offline
import com.hopcape.odo.feature.support.resources.sp_faq_q_pro
import com.hopcape.odo.feature.support.resources.sp_faq_q_reminders
import com.hopcape.odo.feature.support.resources.sp_faq_q_routes
import com.hopcape.odo.feature.support.resources.sp_faq_q_scan
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One question and its answer, before either has been resolved to text.
 *
 * [id] is what the list keys on and what an expanded row is remembered by. The string
 * resource itself cannot do that job: resolved text changes with the device language, and a
 * row would collapse under the owner if it did.
 */
internal data class FaqEntry(
    val id: String,
    val question: StringResource,
    val answer: StringResource,
)

/**
 * The questions, in the order they are shown.
 *
 * Ordered by how often they are likely to be asked, not by topic: someone who opens FAQs is
 * usually stuck on the thing at the top, and a list sorted into neat sections buries it.
 *
 * Content notes live with the strings themselves. The short version: these describe what
 * the app does today, and two of them repeat privacy claims that have to keep matching the
 * published policy.
 */
internal val FAQ_ENTRIES: List<FaqEntry> = listOf(
    FaqEntry("offline", Res.string.sp_faq_q_offline, Res.string.sp_faq_a_offline),
    FaqEntry("auto-odo", Res.string.sp_faq_q_auto_odo, Res.string.sp_faq_a_auto_odo),
    FaqEntry("background", Res.string.sp_faq_q_background, Res.string.sp_faq_a_background),
    FaqEntry("routes", Res.string.sp_faq_q_routes, Res.string.sp_faq_a_routes),
    FaqEntry("scan", Res.string.sp_faq_q_scan, Res.string.sp_faq_a_scan),
    FaqEntry("fairness", Res.string.sp_faq_q_fairness, Res.string.sp_faq_a_fairness),
    FaqEntry("no-benchmark", Res.string.sp_faq_q_no_benchmark, Res.string.sp_faq_a_no_benchmark),
    FaqEntry("health", Res.string.sp_faq_q_health, Res.string.sp_faq_a_health),
    FaqEntry("reminders", Res.string.sp_faq_q_reminders, Res.string.sp_faq_a_reminders),
    FaqEntry("pro", Res.string.sp_faq_q_pro, Res.string.sp_faq_a_pro),
    FaqEntry("car-count", Res.string.sp_faq_q_car_count, Res.string.sp_faq_a_car_count),
    FaqEntry("delete", Res.string.sp_faq_q_delete, Res.string.sp_faq_a_delete),
)

/** A question and answer as text, ready to show or to search. */
internal data class ResolvedFaq(
    val id: String,
    val question: String,
    val answer: String,
)

/**
 * Resolves every entry to text once, for the calling composition.
 *
 * Both screens need the resolved strings — the list to show them, search to match on them —
 * and `stringResource` can only be called from a composable. Resolving the whole catalog up
 * front keeps search matching on answers as well as questions, which is the difference
 * between finding "background location" and only ever finding words in a title.
 */
@Composable
internal fun rememberResolvedFaqs(): List<ResolvedFaq> = FAQ_ENTRIES.map {
    ResolvedFaq(
        id = it.id,
        question = stringResource(it.question),
        answer = stringResource(it.answer),
    )
}

/**
 * The entries matching [query], or none at all when nothing has been typed.
 *
 * Answers are searched as well as questions, so "background" reaches the permission answer
 * whose title never uses the word. Substring rather than whole-word: somebody typing
 * "insur" should not have to finish it.
 *
 * A blank query returns nothing rather than everything. The screen shows a prompt in that
 * case, and returning the full catalog would make it show a second copy of the FAQ list.
 */
internal fun List<ResolvedFaq>.matching(query: String): List<ResolvedFaq> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return filter {
        it.question.contains(trimmed, ignoreCase = true) ||
            it.answer.contains(trimmed, ignoreCase = true)
    }
}
