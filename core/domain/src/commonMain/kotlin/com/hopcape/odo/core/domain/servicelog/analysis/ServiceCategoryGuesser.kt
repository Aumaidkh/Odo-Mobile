package com.hopcape.odo.core.domain.servicelog.analysis

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory

/**
 * Works out which job a bill line describes, from the words the workshop wrote on it.
 *
 * A scanned bill arrives as free text ("Engine oil 5W-30", "Front brake pad set"), and a
 * line with no category cannot be benchmarked at all — the fairness pool is keyed by
 * category. This is the rule that turns one into the other. It is deterministic and pure, so
 * the same bill always reads the same way and the mapping can be argued with in a test
 * rather than in a model's output.
 *
 * **It only recognises whole jobs, never the parts inside one.** A city average for
 * `OIL_CHANGE` is what an oil change costs — oil, filter and labour together — so tagging a
 * ₹400 air filter with `GENERAL_SERVICE` would compare it to a ₹4,200 full service and
 * report a saving that nobody made. Labour, consumables and anything unrecognised stay
 * `null`, and the report carries them through unjudged, which is the honest reading of "we
 * don't know what this line was".
 */
object ServiceCategoryGuesser {

    /**
     * The job [label] names, or `null` when nothing here is confident enough to benchmark.
     *
     * Matching is case-insensitive and by substring, because bills write the same job a
     * dozen ways ("A/C gas top up", "AC GAS REFILLING"). The first rule that matches wins,
     * so the list is ordered most specific first: "brake oil" is brake work, not an oil
     * change, and would otherwise be caught by whichever rule came first.
     */
    fun of(label: String?): ServiceCategory? {
        val text = label?.lowercase()?.trim().orEmpty()
        if (text.isEmpty()) return null
        return RULES.firstOrNull { (_, keywords) -> keywords.any { it in text } }?.first
    }

    /**
     * Category to the words that mean it. Ordered, not a map: BRAKES before OIL_CHANGE so a
     * brake fluid line is brake work, and AC before ELECTRICAL so an AC compressor is not
     * read as wiring.
     */
    private val RULES: List<Pair<ServiceCategory, List<String>>> = listOf(
        ServiceCategory.BRAKES to listOf(
            "brake", "disc pad", "brake pad", "brake shoe", "caliper",
        ),
        ServiceCategory.TYRES to listOf(
            "tyre", "tire", "wheel alignment", "wheel balancing", "wheel balance", "puncture", "alloy wheel",
        ),
        ServiceCategory.AC to listOf(
            "ac gas", "a/c gas", "ac service", "a/c service", "air conditioning", "ac compressor", "cooling coil",
        ),
        ServiceCategory.BATTERY to listOf(
            "battery",
        ),
        ServiceCategory.SUSPENSION to listOf(
            "suspension", "shock absorber", "shocker", "strut", "leaf spring",
        ),
        ServiceCategory.ELECTRICAL to listOf(
            "wiring", "alternator", "starter motor", "self motor", "fuse box", "rewiring",
        ),
        ServiceCategory.OIL_CHANGE to listOf(
            "engine oil", "oil change", "oil filter", "motor oil", "lubricant change",
        ),
        ServiceCategory.GENERAL_SERVICE to listOf(
            "general service", "periodic service", "full service", "major service", "minor service",
            "paid service", "free service", "routine service",
        ),
    )
}
