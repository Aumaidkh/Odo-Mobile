package com.hopcape.odo.core.domain.refuel

/**
 * Decides whether a payment was made at a fuel station.
 *
 * A rule table rather than anything learned, because the input is a merchant name written by
 * a payment app and the answer has to be explainable to the owner. When Odo asks "was this a
 * fuel fill?", the reason it asked has to be something a person would agree with.
 *
 * The table is deliberately biased towards missing fills rather than inventing them. A fill
 * Odo did not detect costs the owner the ten seconds of logging it themselves; a payment
 * wrongly detected as fuel puts a fabricated record in a history whose whole value is that
 * someone can trust it at resale.
 *
 * Names are matched on normalised text so "HP PETROL PUMP", "H.P. Petrol Pump" and
 * "hp petrol pump, karol bagh" are one merchant. The same normalisation produces the key an
 * ignored merchant is stored under, so rejecting one spelling rejects all of them.
 */
object FuelMerchantClassifier {

    /**
     * Brand names that only ever appear on fuel retail.
     *
     * Every entry here is a fuel company, not a word that turns up near fuel. That is why
     * "reliance" is absent while "jio-bp" is present: Reliance sells groceries and phone
     * plans under the same name, and one wrong match is worse than one missed fill.
     */
    private val brands = setOf(
        "bharat petroleum", "bpcl", "hindustan petroleum", "hpcl", "indian oil", "indianoil",
        "iocl", "nayara", "jio bp", "jiobp", "essar oil", "shell", "total energies",
        "totalenergies", "chevron", "texaco", "exxon", "mobil", "petronas", "caltex",
        "sinopec", "repsol", "lukoil", "circle k", "petro canada", "petrobras", "ampol",
        "pertamina", "adnoc", "enoc", "eneos", "ptt station",
    )

    /**
     * Words that describe a fuel station in any market.
     *
     * These carry the non-branded pumps, which is most of them outside a city — an
     * independent forecourt is "shree petroleum filling station" and belongs to no chain.
     */
    private val descriptors = setOf(
        "petrol pump", "petrol bunk", "filling station", "fuel station", "gas station",
        "service station", "petroleum", "fuel point", "fuel centre", "fuel center",
        "petrol station", "diesel pump", "cng station", "cng pump", "charging station",
    )

    /**
     * Words that put a fuel-branded merchant out of the running.
     *
     * Fuel companies run shops, cafes and lubricant counters under the same brand, and a
     * payment at one of those is not a fill. Checked before the brand list so that
     * "Bharat Petroleum Store" is refused rather than matched.
     */
    private val excluded = setOf(
        "store", "mart", "shop", "cafe", "restaurant", "kirana", "grocery", "atm",
        "lubricant", "lubricants", "service centre", "service center", "workshop",
        "garage", "car wash", "carwash", "tyre", "tyres", "parking", "toll",
    )

    /**
     * Whether [merchant] reads as a fuel station.
     *
     * [ignoredKeys] are merchants the owner has already rejected, matched on the same key
     * [keyFor] produces. They win over every rule below: the owner saying "this is not fuel"
     * is a better answer than any table.
     */
    fun isFuelMerchant(merchant: String, ignoredKeys: Set<String> = emptySet()): Boolean {
        val normalised = normalise(merchant)
        if (normalised.isEmpty()) return false
        if (keyFor(merchant) in ignoredKeys) return false
        if (excluded.any { normalised.containsWord(it) }) return false
        return brands.any { normalised.contains(it) } || descriptors.any { normalised.contains(it) }
    }

    /**
     * The stable key a merchant is remembered under.
     *
     * Location suffixes are kept: "HP Retail, Karol Bagh" and "HP Retail, Andheri" are two
     * merchants, and rejecting one should not silence the other. What is dropped is only
     * punctuation, case and spacing, which are the ways one merchant spells itself twice.
     */
    fun keyFor(merchant: String): String = normalise(merchant).replace(" ", "")

    /** Lowercase, punctuation as spaces, runs of spaces collapsed. */
    private fun normalise(text: String): String = text
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    /**
     * Whether [phrase] appears as whole words rather than inside a longer one.
     *
     * "store" must not match "storeys", and more to the point "tyre" must not knock out a
     * station whose name happens to contain it. The receiver is already normalised, so
     * padding both sides with spaces is enough to anchor the ends.
     */
    private fun String.containsWord(phrase: String): Boolean =
        " $this ".contains(" $phrase ")
}
