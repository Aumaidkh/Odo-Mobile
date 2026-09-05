package com.hopcape.odo.feature.billcheck.domain.matching

/**
 * Names what a bill line is, from the words a workshop actually prints.
 *
 * The whole feature rests on this. A band, a schedule interval and the owner's own record are
 * all keyed by the job, so a line nobody can name produces no finding at all — and the screen
 * has to say so rather than tick it as fine.
 *
 * **Rules, not a model** (AI_ADVISORY_PLAN §1). A rule table is free, offline, instant, and —
 * more to the point — inspectable: when a bill is read wrongly, the fix is a phrase in this
 * file rather than a prompt nobody can reason about. The model is for what these cannot name,
 * and that is a later slice.
 *
 * Order is the algorithm. Every rule is checked most-specific first, because Indian bill
 * wording overlaps in ways that a naive contains-check gets backwards:
 *
 * - **"brake oil"** is brake *fluid*, not engine oil. Both contain "oil".
 * - **"oil filter"** is its own priced job, and contains "oil".
 * - **"AC service"** is priced; the bare "ac" category is a different, coarser row.
 * - **"wheel alignment and balancing"** is one job here, and contains "wheel".
 */
internal class BillLineMatcher {

    fun match(line: String): LineMatch {
        val text = line.normalise()
        if (text.isEmpty()) return LineMatch.Unknown
        NOT_A_JOB.firstOrNull { text.containsPhrase(it) }?.let { return LineMatch.NotAJob }
        val whole = text.kind() ?: return LineMatch.Unknown
        // One price covering two jobs cannot be checked against either of them. "Engine oil +
        // filter" at Rs. 5,800 read as an oil filter is a Rs. 5,000 finding that is not true,
        // put in front of an owner at a counter.
        if (line.lowercase().coversMoreThan(whole)) return LineMatch.Unknown
        return LineMatch.Job(whole)
    }

    /**
     * Whether the line covers more than the one job it reads as.
     *
     * Lower-cased before splitting, because a workshop prints "AND" as often as "and" and the
     * joiner is what the split turns on.
     *
     * Two tells, because one is not enough:
     *
     * - **A half names a different job.** "Engine oil + filter" reads as an oil filter whole
     *   and as engine oil in its first half.
     * - **A half names no job but uses a job's own word.** "Engine oil and filter" reads as
     *   engine oil whole, and "filter" names nothing on its own — yet "filter" is a word two
     *   priced jobs are made of, so the line is covering something the band does not.
     *
     * Neither fires on "wheel alignment & balancing": both halves say wheel alignment, and
     * both words belong to it.
     *
     * Deliberately blunt. The cost of calling a combined line unchecked is one line the owner
     * is told nothing about; the cost of getting it wrong is a rupee figure that is false.
     */
    private fun String.coversMoreThan(whole: JobKind): Boolean {
        val parts = split(*JOINERS).map { it.normalise() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return false
        val ownWords = RULES.first { it.kind == whole }.phrases.flatMap { it.split(" ") }.toSet()
        return parts.any { part ->
            val kind = part.kind()
            if (kind != null) kind != whole
            else part.split(" ").any { it in JOB_WORDS && it !in ownWords }
        }
    }

    /** The most specific job this text names, or null. */
    private fun String.kind(): JobKind? =
        RULES.firstOrNull { rule -> rule.phrases.any { containsPhrase(it) } }?.kind

    private data class Rule(val kind: JobKind, val phrases: List<String>)

    private companion object {

        /**
         * Lower-cased, punctuation flattened to spaces, and the abbreviations a bill actually
         * uses spelled out. `a/c` and `a.c.` are the same word as `ac`, and a workshop writes
         * all three.
         *
         * `advisory-classify`'s `labelKey` repeats this, because it is the key its cache is
         * stored under. The two have to stay the same: drift, and a phrase promoted into
         * [RULES] keeps being asked about there under a key nothing matches.
         */
        fun String.normalise(): String =
            lowercase()
                .replace("a/c", "ac")
                .replace("a.c.", "ac")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        /** Whole-word containment, so "oil" does not match inside "boiler". */
        fun String.containsPhrase(phrase: String): Boolean =
            " $this ".contains(" $phrase ")

        /** How a bill joins two jobs onto one priced line. */
        val JOINERS = arrayOf("+", "&", " and ", ",", "/")

        /**
         * Every word any job phrase is made of.
         *
         * Used to spot a half that names no job by itself and still carries a job's word —
         * "filter" in "engine oil and filter". Built from the rules so a phrase added later
         * is covered without a second list to keep in step.
         */
        val JOB_WORDS: Set<String> by lazy {
            RULES.flatMap { rule -> rule.phrases.flatMap { it.split(" ") } }.toSet()
        }

        /**
         * Lines that are not jobs. Checked first: "labour charges for ac service" is a labour
         * line, and matching it as an AC service would price a whole job against it.
         */
        val NOT_A_JOB = listOf(
            "labour", "labor", "consumables", "consumable", "sundry", "sundries",
            "misc", "miscellaneous", "gst", "cgst", "sgst", "igst", "tax", "taxes",
            "discount", "round off", "rounding", "shop supplies", "service charge",
        )

        /**
         * Most specific first. Moving a rule up or down this list changes what a bill reads
         * as, so treat the order as part of the rule.
         */
        val RULES = listOf(
            // Filters before the fluids they are named after.
            Rule(JobKind.OIL_FILTER, listOf("oil filter", "oil fltr", "filter oil")),
            Rule(JobKind.AIR_FILTER, listOf("air filter", "air fltr", "air cleaner element")),

            // "brake oil" is what half of India calls brake fluid.
            Rule(JobKind.BRAKE_FLUID, listOf("brake fluid", "brake oil", "brake liquid")),
            Rule(JobKind.BRAKE_PADS, listOf("brake pad", "brake pads", "brake shoe", "brake shoes")),
            Rule(JobKind.BRAKE_DISC, listOf("brake disc", "brake rotor", "disc rotor", "disc turning")),
            Rule(JobKind.BRAKES, listOf("brake", "brakes", "braking")),

            Rule(JobKind.COOLANT, listOf("coolant", "radiator coolant", "antifreeze")),

            // Before the coarser "ac" row, and before anything matching "service".
            Rule(
                JobKind.AC_SERVICE,
                listOf(
                    "ac service", "ac servicing", "ac gas", "ac gas refill", "ac gas top up",
                    "ac recharge", "aircon service", "air conditioning service",
                ),
            ),

            Rule(JobKind.WHEEL_ALIGNMENT, listOf("wheel alignment", "alignment", "wheel balancing", "balancing")),
            Rule(JobKind.TYRE_ROTATION, listOf("tyre rotation", "tire rotation", "wheel rotation")),
            Rule(JobKind.TYRES, listOf("tyre", "tyres", "tire", "tires")),

            Rule(JobKind.WIPERS, listOf("wiper", "wipers", "wiper blade", "wiper blades")),
            Rule(JobKind.BATTERY, listOf("battery", "batteries")),
            Rule(JobKind.CLUTCH, listOf("clutch", "clutch plate", "clutch kit")),
            Rule(
                JobKind.SUSPENSION,
                listOf("suspension", "shock absorber", "shocker", "shockers", "strut", "struts"),
            ),
            Rule(JobKind.ELECTRICAL, listOf("electrical", "wiring", "alternator", "starter motor")),
            Rule(
                JobKind.DENTING_PAINTING,
                listOf("denting", "painting", "dent", "paint", "body shop", "bodyshop"),
            ),

            // Engine oil last among the oils, so the filter and the brake fluid win first.
            Rule(
                JobKind.ENGINE_OIL,
                listOf("engine oil", "motor oil", "5w30", "5w 30", "5w40", "5w 40", "10w30", "10w 30"),
            ),
            Rule(JobKind.OIL_CHANGE, listOf("oil change", "oil replacement")),

            // Last of all: "service" appears inside half the lines above.
            Rule(
                JobKind.GENERAL_SERVICE,
                listOf(
                    "general service", "periodic service", "paid service", "free service",
                    "periodic maintenance", "running repair",
                ),
            ),
        )
    }
}
