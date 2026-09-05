package com.hopcape.odo.feature.billcheck.domain.matching

import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.advisory.BillLineClassifier
import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.advisory.matching.JobKind
import com.hopcape.odo.core.domain.advisory.matching.LineMatch

/**
 * What a bill line is — the rules first, then the model for what they could not name.
 *
 * **One namer, because two of them disagreed.** The check had the model fallback and the
 * "How we know" sheet did not, so a line the model named was flagged with a band the sheet
 * then refused to explain: the owner tapped it and got "couldn't read this bill", on the one
 * screen whose whole job is showing where a figure came from.
 *
 * Anything that asks what a line is asks here, so there is one answer rather than two.
 */
internal class BillLineNamer(
    private val matcher: BillLineMatcher,
    private val classifier: BillLineClassifier,
    private val config: FeatureConfig,
) {

    /** What the rules alone make of [label]. No network, and the same answer every time. */
    fun byRules(label: String): LineMatch = matcher.match(label)

    /**
     * Every label the rules could not name, named by the model.
     *
     * Only [LineMatch.Unknown] is sent. A [LineMatch.NotAJob] line is one the rules were
     * certain about, and a model reading "labour charges for AC service" as an AC service
     * would price a whole job against a labour line.
     *
     * Off by default — see [FeatureConfig.advisoryClassifierEnabled]. Off, nothing leaves the
     * device. Asked here rather than at construction, so a flip lands on the next read.
     *
     * A slug this app has no [JobKind] for is dropped: the server's catalogue is the longer
     * list, and a slug nothing here can look up is not an answer.
     */
    suspend fun byModel(labels: List<String>): Map<String, JobKind> {
        if (!config.advisoryClassifierEnabled) return emptyMap()
        val askable = labels.filterNot { it.looksPersonal() }.distinct()
        if (askable.isEmpty()) return emptyMap()

        val bySlug = JobKind.entries.associateBy { it.slug }
        return classifier.classify(askable)
            .mapNotNull { (label, slug) -> bySlug[slug]?.let { label to it } }
            .toMap()
    }

    /**
     * One line, named however it can be. For a caller holding a single label rather than a
     * bill — the sheet that explains one finding.
     */
    suspend fun name(label: String): JobKind? =
        (byRules(label) as? LineMatch.Job)?.kind
            ?: byModel(listOf(label))[label]

    /**
     * Whether this line carries something about the owner rather than about a job.
     *
     * Line labels come from OCR, and a header the scanner read as a line item can carry the
     * plate, a phone number or an email. A line like that names no job anyway, so the only
     * thing sending it achieves is putting it in a server-side table that keeps it. Dropped
     * before the request rather than redacted after it.
     */
    private fun String.looksPersonal(): Boolean = PERSONAL.any { it.containsMatchIn(this) }

    private companion object {
        /** An Indian registration number, a ten-digit phone, an email. */
        val PERSONAL = listOf(
            Regex("""\b[A-Za-z]{2}[\s-]?\d{1,2}[\s-]?[A-Za-z]{0,3}[\s-]?\d{4}\b"""),
            Regex("""\b\d{10}\b"""),
            Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""),
        )
    }
}
