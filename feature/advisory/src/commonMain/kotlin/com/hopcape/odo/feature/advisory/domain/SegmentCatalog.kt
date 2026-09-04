package com.hopcape.odo.feature.advisory.domain

import com.hopcape.odo.core.domain.shared.VehicleSegment

/**
 * Which body class a model belongs to.
 *
 * Hand-entered, and deliberately short: it lists the models that actually sell in India in
 * volume, and answers [DEFAULT] for anything else. A wrong segment widens the estimate by
 * one band, which is a worse answer; a missing one would be no answer at all.
 *
 * Matching is on the model name alone, lower-cased. The trim never changes the body class,
 * and the make is redundant once the model is known — no two makes ship a "Baleno".
 */
internal object SegmentCatalog {

    /** Hatchbacks outnumber everything else in the target market, so they are the fallback. */
    val DEFAULT: VehicleSegment = VehicleSegment.HATCHBACK

    fun segmentOf(model: String): VehicleSegment =
        BY_MODEL[model.trim().lowercase()] ?: DEFAULT

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
