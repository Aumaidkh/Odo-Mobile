package com.hopcape.odo.core.data.challan

/**
 * The no-credentials stand-in for the challan records source, mirroring the other
 * `Fake*RemoteDataSource`s: a build without Supabase keys still has a working challans
 * screen, seeded with the states the mockups drew (pending, in-court, clean, not-found).
 *
 * Deterministic on purpose — the same plate always answers the same way, so a manual
 * test can be described in a bug report.
 */
class FakeChallanRemoteDataSource : ChallanRemoteDataSource {

    private val paid = mutableSetOf<String>()

    override suspend fun fetch(regNo: String): ChallanFetchDto = when {
        // A plate ending in an odd digit reads as "not in the records".
        regNo.lastOrNull()?.digitToIntOrNull()?.rem(2) == 1 ->
            ChallanFetchDto(vehicleKnown = false, challans = emptyList())

        else -> ChallanFetchDto(
            vehicleKnown = true,
            challans = sample(regNo).map { row ->
                if (row.id in paid && row.status == STATUS_PENDING) row.copy(status = STATUS_PAID) else row
            },
        )
    }

    override suspend fun markAllPendingPaid(regNo: String) {
        sample(regNo).forEach { paid += it.id }
    }

    private fun sample(regNo: String): List<ChallanDto> = listOf(
        ChallanDto(
            id = "${regNo}20260814004521",
            regNo = regNo,
            violation = "Red light violation",
            amountPaise = 1_000_00,
            location = "Baner Road, Pune",
            issuedOn = "2026-08-14",
            status = STATUS_PENDING,
        ),
        ChallanDto(
            id = "${regNo}20260622001883",
            regNo = regNo,
            violation = "No parking",
            amountPaise = 500_00,
            location = "FC Road, Pune",
            issuedOn = "2026-06-22",
            status = STATUS_PENDING,
        ),
        ChallanDto(
            id = "${regNo}20251102000914",
            regNo = regNo,
            violation = "Driving without licence",
            amountPaise = 5_000_00,
            location = "Shivajinagar, Pune",
            issuedOn = "2025-11-02",
            status = STATUS_IN_COURT,
            courtName = "Shivajinagar, Pune",
            nextHearingOn = "2026-09-04",
        ),
        ChallanDto(
            id = "${regNo}20250918003402",
            regNo = regNo,
            violation = "Over-speeding",
            amountPaise = 1_000_00,
            location = "Nashik Highway",
            issuedOn = "2025-09-18",
            status = STATUS_PAID,
        ),
    )

    private companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PAID = "PAID"
        const val STATUS_IN_COURT = "IN_COURT"
    }
}
