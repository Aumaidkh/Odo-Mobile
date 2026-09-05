package com.hopcape.odo.core.data.advisory

import com.hopcape.odo.core.domain.advisory.BillLineClassifier

/**
 * The classifier for a build with no backend.
 *
 * Names nothing, which is the truthful answer rather than a placeholder: the model runs behind
 * an Edge Function, and a build with no Supabase credentials cannot reach one. The check falls
 * back to its rule table and the unnamed lines stay unchecked — the same screen every build
 * shows today, since the flag that turns this on ships off.
 *
 * `supabaseModule` replaces it with the real adapter the moment the build has credentials.
 */
internal class OfflineBillLineClassifier : BillLineClassifier {

    override suspend fun classify(labels: List<String>): Map<String, String> = emptyMap()
}
