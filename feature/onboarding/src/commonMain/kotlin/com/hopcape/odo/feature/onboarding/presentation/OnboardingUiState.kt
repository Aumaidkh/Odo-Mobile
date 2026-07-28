package com.hopcape.odo.feature.onboarding.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * The steps of first-run setup, in order. Step 1 is the Welcome screen (its own
 * destination, no progress chrome), so the flow destination starts at [CAR].
 *
 * [position] is what the header renders ("2 / 4") and what drives the progress bar,
 * which is why it is stated once here rather than at each call site.
 */
internal enum class OnboardingStep(val position: Int) {
    CAR(2),
    PROFILE(3),
    FIRST_SCAN(4),
    ;

    /** The step before this one, or `null` at the first step (where back leaves the flow). */
    val previous: OnboardingStep?
        get() = entries.getOrNull(ordinal - 1)

    /** The step after this one, or `null` at the last (where continuing finishes onboarding). */
    val next: OnboardingStep?
        get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** Total steps counted by the header, Welcome included. */
        const val TOTAL: Int = 4
    }
}

/**
 * What the RTO lookup returned for a plate — the "is this your car?" confirmation card.
 * Held separately from [CarForm]'s own fields because it is a *suggestion* the owner
 * confirms or rejects, not something they typed.
 */
@Immutable
internal data class RtoMatch(
    val make: String,
    val model: String,
    val variant: String?,
    val year: Int,
    val fuelType: FuelType,
) {
    /** "Maruti Swift VXI" — make, model, and trim as one line. */
    val title: String get() = listOfNotNull(make, model, variant).joinToString(" ")
}

/**
 * Why a plate lookup came back empty. Each reason gets its own copy and its own next step,
 * because "we've never seen this plate" and "we couldn't reach the RTO" are different
 * problems to the owner — one is permanent, the others are worth retrying.
 */
internal enum class PlateLookupError {
    /** The service answered, and has no record for this plate. Retrying won't help. */
    NOT_FOUND,

    /** No connectivity — the request never left the phone. */
    OFFLINE,

    /** The lookup service failed or timed out. Retry is the right move. */
    SERVICE,
}

/**
 * The plate lookup as a state machine, because the owner sees all four states: nothing
 * typed yet, the wait, the answer, and the failure. Modelling it as one value (rather than
 * a `match` plus a couple of booleans) makes the impossible combinations unrepresentable —
 * there is no "loading with a stale match still on screen".
 */
internal sealed interface PlateLookup {
    /** Nothing to look up yet — the plate is blank or still being typed. */
    data object Idle : PlateLookup

    /** A lookup is in flight for the current plate. */
    data object Loading : PlateLookup

    /** The service resolved the plate to a car, pending the owner's confirmation. */
    data class Found(val match: RtoMatch) : PlateLookup

    /** The lookup failed; [reason] decides the copy and whether retry is offered. */
    data class Failed(val reason: PlateLookupError) : PlateLookup
}

/**
 * The car being set up. Two ways in, one shape out: the plate route ([registrationNumber]
 * → [lookup]) and manual entry ([manualEntry] = true), which is where [make]/[model]/[year]/
 * [fuelType] are answered by hand. Odometer is captured on both routes because Odo cannot
 * compute ₹/km, health, or km anomalies without it.
 */
@Immutable
internal data class CarForm(
    val registrationNumber: String = "",
    val lookup: PlateLookup = PlateLookup.Idle,
    val odometerKm: Long? = null,
    val manualEntry: Boolean = false,
    val make: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val year: Int? = null,
    val fuelType: FuelType? = null,
) {
    /** The resolved car, if the lookup found one — the only place a match comes from. */
    val match: RtoMatch? get() = (lookup as? PlateLookup.Found)?.match

    /**
     * Whether the plate is long enough to be worth a lookup. Indian plates run 9–11
     * characters normalized (`MH12AB1234`, `DL8CAF5031`, `22BH1234AA`), so anything
     * shorter is still being typed and a request would only waste a round trip.
     */
    val isPlateLookupReady: Boolean get() = registrationNumber.length in MIN_PLATE_LENGTH..MAX_PLATE_LENGTH

    /** The plate route is answered once a match is confirmed and the odometer is set. */
    private val autoComplete: Boolean get() = match != null && odometerKm != null

    /** Manual entry needs every field the `Car` aggregate validates on. */
    private val manualComplete: Boolean
        get() = !make.isNullOrBlank() && !model.isNullOrBlank() && year != null && fuelType != null

    /** Whether this step's Continue should be enabled. */
    val canContinue: Boolean get() = if (manualEntry) manualComplete else autoComplete

    private companion object {
        const val MIN_PLATE_LENGTH = 9
        const val MAX_PLATE_LENGTH = 11
    }
}

/**
 * The owner's reason for being here, as the three choices the screen offers.
 *
 * A presentation type, not the domain enum: the copy is goal-shaped ("Stop overpaying")
 * while [OnboardingGoal] is storage-shaped (it mirrors the DB `onboarding_goal` enum).
 * [toDomain] is the one seam between them, so re-wording a card never touches the DB.
 */
internal enum class OnboardingGoalOption {
    STOP_OVERPAYING,
    STAY_HEALTHY,
    SELL_FOR_MORE,
}

/** Presentation choice → the stored [OnboardingGoal] that routes the owner after setup. */
internal fun OnboardingGoalOption.toDomain(): OnboardingGoal = when (this) {
    OnboardingGoalOption.STOP_OVERPAYING -> OnboardingGoal.TRACK_COSTS
    OnboardingGoalOption.STAY_HEALTHY -> OnboardingGoal.NEVER_MISS_RENEWAL
    OnboardingGoalOption.SELL_FOR_MORE -> OnboardingGoal.SELL_SOON
}

/** The last thing asked of the owner: a name to greet them by, and why they came. */
@Immutable
internal data class ProfileForm(
    val name: String = "",
    val goal: OnboardingGoalOption? = null,
) {
    /** A name that is actually a name — a lone space shouldn't unlock Continue. */
    val isNameValid: Boolean get() = name.trim().length >= MIN_NAME_LENGTH

    val canContinue: Boolean get() = isNameValid && goal != null

    private companion object {
        const val MIN_NAME_LENGTH = 2
    }
}

/** One selectable model in the manual-details picker — the catalog's model + trim pair. */
@Immutable
internal data class CarModelOption(val name: String, val variant: String? = null) {
    /** Stable identity for the picker's row keys. */
    val id: String get() = listOfNotNull(name, variant).joinToString("-")
}

/**
 * Everything the first-run flow renders. One state for the whole flow (not per screen)
 * because the steps share the car being built and the header's progress.
 *
 * Reference data ([makes], [models], [years]) is carried here rather than read inside the
 * UI: it comes from the `VehicleCatalog` port once a ViewModel is wired, and until then
 * the route seeds it from [sampleOnboardingState].
 */
@Immutable
internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CAR,
    val car: CarForm = CarForm(),
    val profile: ProfileForm = ProfileForm(),
    val makes: List<String> = emptyList(),
    val popularMakes: List<String> = emptyList(),
    val models: List<CarModelOption> = emptyList(),
    val years: IntRange = DefaultYears,
) {
    /** Continue enabled for the current step; the last step is always skippable. */
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.CAR -> car.canContinue
            OnboardingStep.PROFILE -> profile.canContinue
            OnboardingStep.FIRST_SCAN -> true
        }
}

/**
 * Model years offered by default. The catalog owns the real range (and the upper bound
 * moves with the calendar); this keeps the picker usable until it is wired.
 */
private val DefaultYears: IntRange = 1990..2026

/**
 * Seed state for previews and for the un-wired route — a plausible Pune owner mid-flow.
 * Everything here is placeholder reference data; the catalog replaces it when the
 * ViewModel lands.
 */
internal fun sampleOnboardingState(
    step: OnboardingStep = OnboardingStep.CAR,
): OnboardingUiState = OnboardingUiState(
    step = step,
    car = CarForm(
        registrationNumber = "MH12AB1234",
        lookup = PlateLookup.Found(
            RtoMatch(
                make = "Maruti",
                model = "Swift",
                variant = "VXI",
                year = 2020,
                fuelType = FuelType.PETROL,
            ),
        ),
        odometerKm = 54_000L,
    ),
    profile = ProfileForm(name = "Rahul", goal = OnboardingGoalOption.STOP_OVERPAYING),
    makes = sampleMakes,
    popularMakes = samplePopularMakes,
    models = sampleModels,
)

internal val sampleMakes: List<String> = listOf(
    "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Honda", "Toyota",
    "Kia", "Renault", "Volkswagen", "Skoda", "MG", "Nissan",
)

internal val samplePopularMakes: List<String> = listOf("Maruti Suzuki", "Hyundai", "Tata", "Honda")

internal val sampleModels: List<CarModelOption> = listOf(
    CarModelOption("City", "VX CVT"),
    CarModelOption("City", "V MT"),
    CarModelOption("Amaze", "S MT"),
    CarModelOption("Elevate", "ZX CVT"),
    CarModelOption("Jazz", "VX MT"),
    CarModelOption("WR-V", "SV MT"),
)
