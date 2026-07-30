package com.hopcape.odo.core.domain.car.catalog

/**
 * One selectable entry in the model picker: a model, optionally narrowed to a trim
 * ("Swift" / "Swift VXI").
 *
 * Every model is also offered **without** a variant, and that is deliberate. Trim ladders
 * change with every facelift, so any seeded list will be incomplete — and an owner whose
 * exact trim is missing must still be able to name their car rather than be stuck choosing
 * something that isn't theirs. A wrong trim is worse than no trim: it feeds ₹/km and the
 * fairness benchmarks.
 */
data class CarModel(
    val name: String,
    val variant: String? = null,
) {
    /** "Swift VXI" — model and trim read as one, or just the model when there is no trim. */
    val displayName: String get() = listOfNotNull(name, variant).joinToString(" ")
}
