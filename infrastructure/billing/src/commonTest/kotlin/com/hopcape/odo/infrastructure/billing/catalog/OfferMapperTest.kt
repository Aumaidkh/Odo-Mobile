package com.hopcape.odo.infrastructure.billing.catalog

import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.revenuecat.purchases.kmp.models.InstallmentsInfo
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.Period
import com.revenuecat.purchases.kmp.models.PeriodUnit
import com.revenuecat.purchases.kmp.models.PresentedOfferingContext
import com.revenuecat.purchases.kmp.models.Price
import com.revenuecat.purchases.kmp.models.PricingPhase
import com.revenuecat.purchases.kmp.models.ProductCategory
import com.revenuecat.purchases.kmp.models.ProductType
import com.revenuecat.purchases.kmp.models.PurchasingData
import com.revenuecat.purchases.kmp.models.RecurrenceMode
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreProductDiscount
import com.revenuecat.purchases.kmp.models.SubscriptionOption
import com.revenuecat.purchases.kmp.models.SubscriptionOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the store hands over becomes what the paywall shows.
 *
 * Everything RevenueCat exposes here is an interface, so this runs with no store, no Play
 * account and no device — which is the only reason the mapping is covered at all, given a
 * real purchase needs an internal-track build.
 */
class OfferMapperTest {

    @Test
    fun theStoresOwnPriceStringsAreCarriedThrough() {
        val offer = OfferMapper.map(offering(monthlyPackage(), annualPackage()), onDropped = { _, _ -> })!!

        assertEquals("₹149.00", offer.monthly?.formattedPrice)
        assertEquals("₹1,490.00", offer.annual?.formattedPrice)
        assertEquals(
            "₹124.17",
            offer.annual?.formattedPricePerMonth,
            "the per-month figure is the store's, so Odo never divides or rounds a price",
        )
    }

    @Test
    fun aMonthlyPlansPerMonthPriceIsJustItsPrice() {
        val offer = OfferMapper.map(offering(monthlyPackage()), onDropped = { _, _ -> })!!

        assertEquals("₹149.00", offer.monthly?.formattedPricePerMonth)
    }

    @Test
    fun packageTypeBecomesTheBillingPeriod() {
        val offer = OfferMapper.map(offering(monthlyPackage(), annualPackage()), onDropped = { _, _ -> })!!

        assertEquals(
            listOf(BillingPeriod.MONTHLY, BillingPeriod.ANNUAL),
            offer.plans.map { it.period },
        )
    }

    @Test
    fun aFreeTrialIsFoundByItsPriceBeingZero() {
        val offer = OfferMapper.map(offering(monthlyPackage(trialDays = 7)), onDropped = { _, _ -> })!!

        assertEquals(7, offer.monthly?.freeTrialDays)
    }

    @Test
    fun aTrialInWeeksIsReportedInDays() {
        val trial = Period(value = 1, unit = PeriodUnit.WEEK)
        val offer = OfferMapper.map(offering(monthlyPackage(trialPeriod = trial)), onDropped = { _, _ -> })!!

        assertEquals(7, offer.monthly?.freeTrialDays)
    }

    @Test
    fun aPlanWithNoTrialSaysSoRatherThanGuessing() {
        val offer = OfferMapper.map(offering(monthlyPackage()), onDropped = { _, _ -> })!!

        assertNull(offer.monthly?.freeTrialDays)
    }

    @Test
    fun aPackageOdoCannotShowIsDroppedAndReported() {
        val dropped = mutableListOf<Pair<String, String>>()

        val offer = OfferMapper.map(
            offering(monthlyPackage(), package_(id = "\$rc_lifetime", type = PackageType.LIFETIME)),
            onDropped = { id, type -> dropped += id to type },
        )!!

        assertEquals(1, offer.plans.size)
        assertEquals(listOf("\$rc_lifetime" to "LIFETIME"), dropped, "a dropped package is never silent")
    }

    @Test
    fun anOfferingWithNothingShowableIsNoOfferAtAll() {
        val offer = OfferMapper.map(
            offering(package_(id = "\$rc_weekly", type = PackageType.WEEKLY)),
            onDropped = { _, _ -> },
        )

        assertNull(offer, "the caller turns this into NothingForSale rather than an empty paywall")
    }

    @Test
    fun theOfferingIdentifierIsCarriedForTelemetry() {
        val offer = OfferMapper.map(offering(monthlyPackage()), onDropped = { _, _ -> })!!

        assertTrue(offer.id == "default")
    }

    /* ------------------------------ fakes ------------------------------ */

    private fun monthlyPackage(trialDays: Int? = null, trialPeriod: Period? = null) = package_(
        id = "\$rc_monthly",
        type = PackageType.MONTHLY,
        price = Price(formatted = "₹149.00", amountMicros = 149_000_000, currencyCode = "INR"),
        trial = trialPeriod ?: trialDays?.let { Period(value = it, unit = PeriodUnit.DAY) },
    )

    private fun annualPackage() = package_(
        id = "\$rc_annual",
        type = PackageType.ANNUAL,
        price = Price(formatted = "₹1,490.00", amountMicros = 1_490_000_000, currencyCode = "INR"),
        pricePerMonth = Price(formatted = "₹124.17", amountMicros = 124_166_666, currencyCode = "INR"),
    )

    private fun package_(
        id: String,
        type: PackageType,
        price: Price = Price(formatted = "₹149.00", amountMicros = 149_000_000, currencyCode = "INR"),
        pricePerMonth: Price? = null,
        trial: Period? = null,
    ): Package = FakePackage(id, type, FakeStoreProduct(id, price, pricePerMonth, trial))

    private fun offering(vararg packages: Package): Offering = FakeOffering(packages.toList())
}

private class FakeOffering(override val availablePackages: List<Package>) : Offering {
    override val identifier = "default"
    override val serverDescription = "Odo Pro"
    override val metadata: Map<String, Any> = emptyMap()
    override val webCheckoutUrl: String? = null
    override val lifetime: Package? = null
    override val annual = availablePackages.firstOrNull { it.packageType == PackageType.ANNUAL }
    override val sixMonth: Package? = null
    override val threeMonth: Package? = null
    override val twoMonth: Package? = null
    override val monthly = availablePackages.firstOrNull { it.packageType == PackageType.MONTHLY }
    override val weekly: Package? = null
}

private class FakePackage(
    override val identifier: String,
    override val packageType: PackageType,
    override val storeProduct: StoreProduct,
) : Package {
    override val presentedOfferingContext = PresentedOfferingContext("default", null, null)
    override val webCheckoutUrl: String? = null
}

private class FakeStoreProduct(
    override val id: String,
    override val price: Price,
    override val pricePerMonth: Price?,
    private val trial: Period?,
) : StoreProduct {
    override val type = ProductType.SUBS
    override val category = ProductCategory.SUBSCRIPTION
    override val title = "Odo Pro"
    override val localizedDescription = "Odo Pro"
    override val period = Period(value = 1, unit = PeriodUnit.MONTH)
    override val subscriptionOptions: SubscriptionOptions? =
        trial?.let { FakeSubscriptionOptions(FakeSubscriptionOption(it, price)) }
    override val defaultOption: SubscriptionOption? = null
    override val discounts: List<StoreProductDiscount> = emptyList()
    override val introductoryDiscount: StoreProductDiscount? = null
    override val purchasingData = FakePurchasingData(id)
    override val presentedOfferingContext = PresentedOfferingContext("default", null, null)
    override val pricePerWeek: Price? = null
    override val pricePerYear: Price? = null
}

private class FakeSubscriptionOptions(private val trial: SubscriptionOption) : SubscriptionOptions {
    override val basePlan: SubscriptionOption? = null
    override val freeTrial = trial
    override val introOffer: SubscriptionOption? = null
    override val defaultOffer = trial
    override fun withTag(tag: String): List<SubscriptionOption> = emptyList()
}

private class FakeSubscriptionOption(trial: Period, fullPrice: Price) : SubscriptionOption {
    override val id = "trial"
    override val pricingPhases = listOf(
        PricingPhase(
            billingPeriod = trial,
            recurrenceMode = RecurrenceMode.NON_RECURRING,
            billingCycleCount = 1,
            price = Price(formatted = "₹0.00", amountMicros = 0, currencyCode = fullPrice.currencyCode),
            offerPaymentMode = null,
        ),
        PricingPhase(
            billingPeriod = Period(value = 1, unit = PeriodUnit.MONTH),
            recurrenceMode = RecurrenceMode.INFINITE_RECURRING,
            billingCycleCount = null,
            price = fullPrice,
            offerPaymentMode = null,
        ),
    )
    override val tags: List<String> = emptyList()
    override val presentedOfferingIdentifier: String? = null
    override val presentedOfferingContext = PresentedOfferingContext("default", null, null)
    override val purchasingData = FakePurchasingData("trial")
    override val installmentsInfo: InstallmentsInfo? = null
}

private class FakePurchasingData(override val productId: String) : PurchasingData {
    override val productType = ProductType.SUBS
}
