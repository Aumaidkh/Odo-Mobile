package com.hopcape.odo.infrastructure.database.cost

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.infrastructure.database.db.Fuel_price
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceOverrides
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * Fuel prices from the local `fuel_price` table — seeded with the app, refreshable from
 * the server, and overridable by the owner.
 *
 * The table is what keeps a price change from needing a release: the seed is only the
 * starting point, M4's `fuel-prices` feed writes `REMOTE` rows on top of it, and an owner
 * who knows their pump rate writes an `OWNER` row that outranks both.
 *
 * A fuel type with no row at all answers `null` rather than borrowing another city's
 * price — an owner in a city Odo does not cover would otherwise see a wrong number with no
 * way to tell. Every such miss is reported, because an uncovered city and a broken read
 * look identical from the screen.
 */
internal class LocalFuelPriceProvider(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FuelPriceProvider, FuelPriceOverrides {

    private val queries get() = database.fuelPriceQueries

    override suspend fun priceFor(city: String?, fuelType: FuelType): FuelPrice? =
        telemetry.span(DataTelemetry.FUEL_PRICE, OP_PRICE_FOR) {
            withContext(dispatcher) {
                val key = city?.trim()?.lowercase().orEmpty()
                try {
                    val row = queries.selectPrice(fuelType = fuelType.name, city = key).executeAsOneOrNull()
                    if (row == null) {
                        telemetry.missing(DataTelemetry.FUEL_PRICE, OP_PRICE_FOR, "$key/${fuelType.name}")
                    }
                    // The city is echoed back as the owner wrote it, not as the lookup key
                    // it was matched by: "in pune" is Odo's index, "in Pune" is their city.
                    row?.toDomain(city?.trim())
                } catch (e: Exception) {
                    telemetry.crashed(DataTelemetry.FUEL_PRICE, OP_PRICE_FOR, e)
                    // No price means the running cost drops its fuel half and says so. A
                    // thrown read must not take the whole screen down with it.
                    null
                }
            }
        }

    override fun priceChanges(): Flow<Unit> =
        queries.countPrices()
            .asFlow()
            .mapToOne(dispatcher)
            .map { }
            // A reader combines this, so a failure here would take the whole screen's flow
            // down over reference data it can live without. Reported, because a stream that
            // has quietly stopped watching prices looks exactly like prices that never change.
            .catch { cause ->
                telemetry.crashed(DataTelemetry.FUEL_PRICE, OP_PRICE_CHANGES, cause)
                emit(Unit)
            }

    override suspend fun setOverride(
        fuelType: FuelType,
        pricePerUnit: Amount,
        on: LocalDate,
    ): Either<DomainError, Unit> = telemetry.span(DataTelemetry.FUEL_PRICE, OP_SET_OVERRIDE) {
        withContext(dispatcher) {
            try {
                // Delete-then-insert in one transaction: one owner row per fuel type, and
                // SQLite 3.18 (minSdk 26) has no UPSERT to do it in a single statement.
                database.transaction {
                    queries.deleteOverride(fuelType.name)
                    queries.insertPrice(
                        id = "owner-${fuelType.name}".lowercase(),
                        city = "",
                        fuel_type = fuelType.name,
                        paise_per_unit = pricePerUnit.paise,
                        effective_date = on.toString(),
                        source = FuelPriceSource.OWNER.name,
                    )
                }
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.FUEL_PRICE, OP_SET_OVERRIDE, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }
    }

    override suspend fun clearOverride(fuelType: FuelType): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.FUEL_PRICE, OP_CLEAR_OVERRIDE) {
            withContext(dispatcher) {
                try {
                    queries.deleteOverride(fuelType.name)
                    Unit.right()
                } catch (e: Exception) {
                    telemetry.crashed(DataTelemetry.FUEL_PRICE, OP_CLEAR_OVERRIDE, e)
                    DomainError.PersistenceFailure(e.message).left()
                }
            }
        }

    /**
     * An unknown `source` or `fuel_type` would mean a row written by a newer build. The
     * price is dropped rather than guessed at: a rate attributed to the wrong source is
     * shown with the wrong amount of trust.
     */
    private fun Fuel_price.toDomain(requestedCity: String?): FuelPrice? {
        val priceSource = FuelPriceSource.entries.firstOrNull { it.name == source } ?: return null
        val fuel = FuelType.entries.firstOrNull { it.name == fuel_type } ?: return null
        return FuelPrice(
            // An owner's own rate belongs to no city, whatever they were asked about.
            city = if (priceSource == FuelPriceSource.OWNER) null else requestedCity?.ifBlank { null },
            fuelType = fuel,
            pricePerUnit = Amount.of(paise_per_unit).getOrElse { Amount.ZERO },
            effectiveDate = LocalDate.parse(effective_date),
            source = priceSource,
        )
    }

    private companion object {
        const val OP_PRICE_FOR = "priceFor"
        const val OP_PRICE_CHANGES = "priceChanges"
        const val OP_SET_OVERRIDE = "setOverride"
        const val OP_CLEAR_OVERRIDE = "clearOverride"
    }
}
