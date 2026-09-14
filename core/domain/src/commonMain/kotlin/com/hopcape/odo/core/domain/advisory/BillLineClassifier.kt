package com.hopcape.odo.core.domain.advisory

/**
 * Names bill lines the app's own rules could not.
 *
 * The rule table is asked first — free, offline, instant, and fixable by editing a phrase.
 * This is for what it misses: the wording one workshop in one city uses.
 *
 * **It names a job. It never prices one.** The band still comes from the price tables, for a
 * model-named line exactly as for a rules-named one (AI_ADVISORY_PLAN §2.7).
 */
fun interface BillLineClassifier {

    /**
     * The slug for each label it could name, keyed by the label as it was passed in.
     *
     * A label the classifier cannot name is simply absent, and so is every label when it fails
     * or is switched off. There is no error to hand back: an unnamed line is the screen's
     * ordinary "we could not check this", which is what it already shows for a line the rules
     * miss.
     */
    suspend fun classify(labels: List<String>): Map<String, String>
}
