package com.hopcape.odo.infrastructure.supabase.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.AccountEraser
import com.hopcape.odo.core.domain.auth.EraseOutcome
import com.hopcape.odo.core.domain.auth.VerifiedPhoneToken
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment
import com.hopcape.odo.infrastructure.supabase.http.SupabaseJson
import com.hopcape.odo.infrastructure.supabase.observability.SupabaseTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * `POST /functions/v1/legal/delete-account` — the server side of "Delete my account & data".
 *
 * An Edge Function rather than a table write, because the erase needs the service-role key
 * and that has to stay off the device. It removes stored files, then every row the owner
 * has, then the account itself.
 *
 * **The proof has to be fresh.** The function rejects any ID token whose `auth_time` is more
 * than ten minutes old — an ordinary hour-long session is deliberately not enough to
 * authorise something irreversible. That constraint is the reason the deletion flow re-runs
 * the OTP rather than reusing the session it already has, and `stale_verification` coming
 * back here means the owner took too long between the code and the confirm.
 *
 * **The token never reaches a log line**, and neither does the response body: the function's
 * error text can quote the phone number. Only the status and the machine-readable code move.
 */
internal class SupabaseAccountEraser(
    private val client: HttpClient,
    private val environment: SupabaseEnvironment,
    private val telemetry: SupabaseTelemetry,
) : AccountEraser {

    override suspend fun erase(token: VerifiedPhoneToken): Either<DomainError, EraseOutcome> =
        telemetry.span(OP_ERASE, RESOURCE) {
            try {
                send(token)
            } catch (cancellation: CancellationException) {
                // The owner navigated away mid-request. Not a failure, and re-reporting it as
                // one would show an error over a screen that is already gone.
                throw cancellation
            } catch (e: Exception) {
                telemetry.failed(OP_ERASE, RESOURCE, e)
                // Unreachable, not refused. Nothing has been deleted, so a retry is honest.
                DomainError.AccountEraseFailed().left()
            }
        }

    private suspend fun send(token: VerifiedPhoneToken): Either<DomainError, EraseOutcome> {
        val response = client.post("${environment.functionsUrl}/$FUNCTION/$PATH") {
            contentType(ContentType.Application.Json)
            setBody(
                SupabaseJson.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(mapOf("idToken" to JsonPrimitive(token.value))),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            telemetry.rejected(OP_ERASE, RESOURCE, response.status.value)
            val code = runCatching {
                SupabaseJson.decodeFromString(ErrorResponse.serializer(), response.bodyAsText()).code
            }.getOrNull()
            return code.toDomainError().left()
        }

        val body = SupabaseJson.decodeFromString(EraseResponse.serializer(), response.bodyAsText())
        return when (body.status) {
            STATUS_DELETED -> EraseOutcome.DELETED.right()
            STATUS_NO_ACCOUNT -> EraseOutcome.NO_ACCOUNT.right()
            // A 200 with a status nobody here understands. Reporting success would tell the
            // owner their account is gone on the word of a response we could not read.
            else -> DomainError.AccountEraseFailed(body.status).left()
        }
    }

    /**
     * The function's own reason, mapped to something a screen can act on.
     *
     * Only [CODE_STALE_VERIFICATION] leads anywhere different: it means the code was proved
     * too long ago, and the answer is to verify again rather than to retry the erase. Every
     * other code is carried through as-is — it ends up in a support conversation about an
     * account that is still there, and a paraphrase would lose the only clue.
     */
    private fun String?.toDomainError(): DomainError = when (this) {
        CODE_STALE_VERIFICATION -> DomainError.ReVerificationRequired
        else -> DomainError.AccountEraseFailed(this)
    }

    private companion object {
        const val RESOURCE = "legal"
        const val OP_ERASE = "legal.deleteAccount"

        /** The slug `supabase/functions/legal` is deployed under, and its route. */
        const val FUNCTION = "legal"
        const val PATH = "delete-account"

        const val STATUS_DELETED = "deleted"
        const val STATUS_NO_ACCOUNT = "no_account"

        /** `auth_time` older than the function's ten-minute limit. */
        const val CODE_STALE_VERIFICATION = "stale_verification"
    }
}

@Serializable
private data class EraseResponse(@SerialName("status") val status: String)

@Serializable
private data class ErrorResponse(@SerialName("error_code") val code: String? = null)
