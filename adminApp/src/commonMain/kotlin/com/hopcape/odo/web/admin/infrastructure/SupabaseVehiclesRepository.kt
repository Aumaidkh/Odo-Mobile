package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.VehicleMake
import com.hopcape.odo.web.admin.domain.VehicleModel
import com.hopcape.odo.web.admin.domain.VehicleSubmission
import com.hopcape.odo.web.admin.domain.VehiclesRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.encoded
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `vehicle_makes`, `vehicle_models` and `vehicle_catalog_submissions` over PostgREST.
 *
 * Reads are plain selects; the catalog is public and every account sees the same
 * rows. Writes are refused by RLS unless the session holds
 * `catalog.vehicles.write`, and adding goes through `admin_add_vehicle`, which
 * checks the same permission itself because it runs as definer.
 */
internal class SupabaseVehiclesRepository(
    private val postgrest: Postgrest,
) : VehiclesRepository {

    override suspend fun makes(): Either<WebError, List<VehicleMake>> =
        postgrest.select(
            table = TABLE_MAKES,
            serializer = MakeRow.serializer(),
            query = "select=id,name,display_order&order=name.asc",
        ).map { rows -> rows.map { VehicleMake(it.id, it.name, it.displayOrder) } }

    override suspend fun models(): Either<WebError, List<VehicleModel>> =
        postgrest.select(
            table = TABLE_MODELS,
            serializer = ModelRow.serializer(),
            // The whole table in one read. It is a few thousand rows of short
            // text, and the alternative — a request per make as somebody clicks
            // around — makes searching across makes impossible.
            query = "select=id,make_id,name,variant,display_order&order=name.asc,variant.asc",
        ).map { rows ->
            rows.map { VehicleModel(it.id, it.makeId, it.name, it.variant, it.displayOrder) }
        }

    override suspend fun submissions(): Either<WebError, List<VehicleSubmission>> =
        postgrest.select(
            table = TABLE_SUBMISSIONS,
            serializer = VehicleSubmissionRow.serializer(),
            query = "select=id,make,model,variant,status,created_at&order=created_at.asc",
        ).map { rows ->
            rows.map {
                VehicleSubmission(
                    id = it.id,
                    make = it.make,
                    model = it.model,
                    variant = it.variant,
                    status = it.status,
                    createdAt = it.createdAt.substringBefore('T'),
                )
            }
        }

    override suspend fun add(make: String, model: String, variant: String?): Either<WebError, Unit> {
        val variantJson = variant?.let { "\"${it.jsonEscaped()}\"" } ?: "null"
        return postgrest.call(
            name = "admin_add_vehicle",
            body = """{"p_make":"${make.jsonEscaped()}","p_model":"${model.jsonEscaped()}","p_variant":$variantJson}""",
        )
    }

    override suspend fun renameMake(id: String, name: String): Either<WebError, Unit> =
        postgrest.patch(
            table = TABLE_MAKES,
            query = "id=eq.${id.encoded()}",
            body = """{"name":"${name.jsonEscaped()}"}""",
        )

    override suspend fun editModel(id: String, name: String, variant: String?): Either<WebError, Unit> {
        // Written explicitly as null rather than omitted. PostgREST reads an
        // absent key as "leave this column alone", so clearing a trim by leaving
        // the field empty would silently keep the old one.
        val variantJson = variant?.let { "\"${it.jsonEscaped()}\"" } ?: "null"
        return postgrest.patch(
            table = TABLE_MODELS,
            query = "id=eq.${id.encoded()}",
            body = """{"name":"${name.jsonEscaped()}","variant":$variantJson}""",
        )
    }

    override suspend fun deleteMake(id: String): Either<WebError, Unit> =
        postgrest.delete(table = TABLE_MAKES, query = "id=eq.${id.encoded()}")

    override suspend fun deleteModel(id: String): Either<WebError, Unit> =
        postgrest.delete(table = TABLE_MODELS, query = "id=eq.${id.encoded()}")

    override suspend fun decideSubmission(id: String, accepted: Boolean): Either<WebError, Unit> =
        postgrest.patch(
            table = TABLE_SUBMISSIONS,
            query = "id=eq.$id",
            body = """{"status":"${if (accepted) "accepted" else "rejected"}"}""",
        )

    override suspend fun deleteSubmission(id: String): Either<WebError, Unit> =
        postgrest.delete(table = TABLE_SUBMISSIONS, query = "id=eq.$id")

    private companion object {
        const val TABLE_MAKES = "vehicle_makes"
        const val TABLE_MODELS = "vehicle_models"
        const val TABLE_SUBMISSIONS = "vehicle_catalog_submissions"
    }
}

@Serializable
private data class MakeRow(
    val id: String,
    val name: String,
    @SerialName("display_order") val displayOrder: Long,
)

@Serializable
private data class ModelRow(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    val variant: String? = null,
    @SerialName("display_order") val displayOrder: Long,
)

@Serializable
private data class VehicleSubmissionRow(
    val id: String,
    val make: String,
    val model: String,
    val variant: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)
