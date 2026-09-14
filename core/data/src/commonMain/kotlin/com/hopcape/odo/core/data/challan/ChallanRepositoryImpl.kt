package com.hopcape.odo.core.data.challan

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.challan.model.ChallanId
import com.hopcape.odo.core.domain.challan.model.ChallanLookup
import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.core.domain.challan.repository.ChallanRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/**
 * [ChallanRepository] over a local cache and the records source — offline-first for the
 * owner's own car, remote-only for a stranger's plate.
 *
 * This layer owns what an operation means: DTO→domain mapping (a row the source sent
 * malformed is *skipped and reported*, never allowed to sink the whole answer), failure
 * mapping to [DomainError], and telemetry. How rows are stored lives behind [local]; what
 * the source is lives behind [remote].
 *
 * [markAllPendingPaid] writes local-first: the owner's claim lands on their own screen
 * immediately, and the source is told best-effort. A failure to tell the source is not a
 * failure of the operation — today's source is Odo's own table, and a real government
 * source would not accept the claim at all.
 */
internal class ChallanRepositoryImpl(
    private val local: ChallanLocalDataSource,
    private val remote: ChallanRemoteDataSource,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
) : ChallanRepository {

    override fun observe(regNo: RegistrationNumber): Flow<List<Challan>> =
        local.observe(regNo.value)
            .catch { cause ->
                telemetry.crashed(DataTelemetry.CHALLAN, OP_OBSERVE, cause)
                // An unreadable cache reads as an empty one: the screen falls back to its
                // "never checked" state, which offers the refresh that repopulates it.
                emit(emptyList())
            }

    override fun observeLastChecked(regNo: RegistrationNumber) =
        local.observeLastChecked(regNo.value)
            .catch { cause ->
                telemetry.crashed(DataTelemetry.CHALLAN, OP_OBSERVE_CHECKED, cause)
                emit(null)
            }

    override suspend fun refresh(regNo: RegistrationNumber): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.CHALLAN, OP_REFRESH) {
            val fetched = try {
                remote.fetch(regNo.value)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CHALLAN, OP_REFRESH, e)
                return@span DomainError.ChallanRecordsUnreachable.left()
            }
            try {
                // An unknown plate on the owner's own car still stamps the check: "we
                // asked, the records have nothing" is an answer, and the clean screen
                // dates itself by it.
                local.replaceAll(
                    regNo = regNo.value,
                    challans = fetched.challans.mapNotNull { it.toDomainOrNull() },
                    checkedAt = clock.now(),
                )
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CHALLAN, OP_REFRESH, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun markAllPendingPaid(regNo: RegistrationNumber): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.CHALLAN, OP_MARK_PAID) {
            try {
                local.markAllPendingPaid(regNo.value)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CHALLAN, OP_MARK_PAID, e)
                return@span DomainError.PersistenceFailure(e.message).left()
            }
            // Advisory: the owner's screen is already right; a source that refuses the
            // claim is reported, not surfaced.
            try {
                remote.markAllPendingPaid(regNo.value)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CHALLAN, OP_MARK_PAID_REMOTE, e)
            }
            Unit.right()
        }

    override suspend fun lookup(regNo: RegistrationNumber): Either<DomainError, ChallanLookup> =
        telemetry.span(DataTelemetry.CHALLAN, OP_LOOKUP) {
            try {
                val fetched = remote.fetch(regNo.value)
                when {
                    !fetched.vehicleKnown -> ChallanLookup.VehicleNotFound.right()
                    // Deliberately not written to the cache: a buyer's check on someone
                    // else's plate is shown once and saved nowhere.
                    else -> ChallanLookup.Found(fetched.challans.mapNotNull { it.toDomainOrNull() }).right()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CHALLAN, OP_LOOKUP, e)
                DomainError.ChallanRecordsUnreachable.left()
            }
        }

    /**
     * One row, or `null` with a report if the source sent something unusable. A malformed
     * row must cost only itself: three good challans and one broken one is three challans
     * on screen, not an error card.
     */
    private suspend fun ChallanDto.toDomainOrNull(): Challan? {
        val plate = RegistrationNumber.of(regNo) ?: return skip("reg_no")
        val parsedAmount = Amount.of(amountPaise).getOrNull() ?: return skip("amount_paise")
        val date = runCatching { LocalDate.parse(issuedOn) }.getOrNull() ?: return skip("issued_on")
        val parsedStatus = runCatching { ChallanStatus.valueOf(status) }.getOrNull() ?: return skip("status")
        if (id.isBlank() || violation.isBlank()) return skip("identity")
        return Challan(
            id = ChallanId(id),
            regNo = plate,
            violation = violation,
            amount = parsedAmount,
            location = location?.takeIf { it.isNotBlank() },
            issuedOn = date,
            status = parsedStatus,
            courtName = courtName?.takeIf { it.isNotBlank() },
            nextHearingOn = nextHearingOn?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() },
        )
    }

    private suspend fun skip(field: String): Challan? {
        telemetry.missing(DataTelemetry.CHALLAN, OP_MAP, field)
        return null
    }

    private companion object {
        const val OP_OBSERVE = "observe"
        const val OP_OBSERVE_CHECKED = "observeLastChecked"
        const val OP_REFRESH = "refresh"
        const val OP_MARK_PAID = "markAllPendingPaid"
        const val OP_MARK_PAID_REMOTE = "markAllPendingPaid.remote"
        const val OP_LOOKUP = "lookup"
        const val OP_MAP = "map"
    }
}
