package com.hopcape.odo.core.domain.car.catalog

import com.hopcape.odo.core.domain.shared.VehicleSegment

/**
 * Which body class a model belongs to.
 *
 * Hand-entered, and deliberately short: it lists the models that actually sell in India in
 * volume. Matching is on the model name alone, lower-cased — the trim never changes the body
 * class, and the make is redundant once the model is known, since no two makes ship a
 * "Baleno".
 *
 * **A caller has to choose what an unknown model means**, which is why there are two reads.
 * [segmentOrNull] says "not in the list", and [segmentOf] guesses [DEFAULT]. Guessing is the
 * right answer where a rough figure beats none, and the wrong one where the segment feeds a
 * price claim: a bill for an SUV priced against a hatchback's parts is a finding put in front
 * of an owner at a counter, and it is wrong in the direction that costs them an argument.
 */
object SegmentCatalog {

    /** Hatchbacks outnumber everything else in the target market, so they are the fallback. */
    val DEFAULT: VehicleSegment = VehicleSegment.HATCHBACK

    /** The segment, guessing [DEFAULT] for a model the list does not carry. */
    fun segmentOf(model: String): VehicleSegment = segmentOrNull(model) ?: DEFAULT

    /** The segment, or null when the list does not carry the model. */
    fun segmentOrNull(model: String): VehicleSegment? = BY_MODEL[model.trim().lowercase()]

    private val BY_MODEL: Map<String, VehicleSegment> = buildMap {
        listOf(
            "alto", "alto k10", "s-presso", "celerio", "wagon r", "wagonr", "swift", "baleno",
            "ignis", "i10", "grand i10", "grand i10 nios", "i20", "santro", "tiago", "altroz",
            "punch", "kwid", "polo", "figo", "glanza", "micra", "jazz", "comet", "citroen c3",
        ).forEach { put(it, VehicleSegment.HATCHBACK) }

        listOf(
            "dzire", "swift dzire", "ciaz", "aura", "xcent", "verna", "tigor", "amaze", "city",
            "slavia", "virtus", "vento", "rapid", "aspire", "sunny", "yaris", "elantra", "octavia",
        ).forEach { put(it, VehicleSegment.SEDAN) }

        listOf(
            "brezza", "vitara brezza", "grand vitara", "fronx", "jimny", "venue", "creta",
            "alcazar", "tucson", "nexon", "harrier", "safari", "curvv", "xuv300", "xuv 3xo",
            "xuv700", "scorpio", "scorpio n", "thar", "bolero", "sonet", "seltos", "syros",
            "elevate", "wr-v", "kushaq", "kylaq", "taigun", "ecosport", "hector", "astor",
            "gloster", "magnite", "kiger", "duster", "urban cruiser", "hyryder", "taisor",
        ).forEach { put(it, VehicleSegment.SUV) }

        listOf(
            "ertiga", "xl6", "invicto", "innova", "innova crysta", "innova hycross", "rumion",
            "carens", "marazzo", "triber", "carnival", "hexa", "bolero neo",
        ).forEach { put(it, VehicleSegment.MUV) }
    }
}
