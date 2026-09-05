package com.hopcape.odo.feature.support.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.FeatureIdea
import com.hopcape.odo.core.domain.support.FeatureIdeaRepository
import com.hopcape.odo.core.domain.support.SupportTicket
import com.hopcape.odo.core.domain.support.SupportTicketRepository
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.feature.support.presentation.SupportTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Remembers what it was given, and can be told to refuse. */
internal class FakeTickets(private val failing: Boolean = false) : SupportTicketRepository {
    val submitted = mutableListOf<SupportTicket>()
    private val stored = MutableStateFlow<List<SupportTicket>>(emptyList())

    override suspend fun submit(ticket: SupportTicket): Either<DomainError, SupportTicket> {
        if (failing) return DomainError.PersistenceFailure("no").left()
        submitted += ticket
        stored.value = listOf(ticket) + stored.value
        return ticket.right()
    }

    override fun observe(): Flow<List<SupportTicket>> = stored
}

internal class FakeIdeas(initial: List<FeatureIdea> = emptyList()) : FeatureIdeaRepository {
    val votes = mutableListOf<Pair<String, Boolean>>()
    var refreshFails = false
    var voteFails = false
    private val stored = MutableStateFlow(initial)

    override fun observe(): Flow<List<FeatureIdea>> = stored

    override suspend fun refresh(): Either<DomainError, Unit> =
        if (refreshFails) DomainError.LookupUnavailable.left() else Unit.right()

    override suspend fun vote(ideaId: String, voted: Boolean): Either<DomainError, Unit> {
        if (voteFails) return DomainError.PersistenceFailure("no").left()
        votes += ideaId to voted
        stored.value = stored.value.map { idea ->
            if (idea.id != ideaId) idea else {
                idea.copy(voted = voted, votes = idea.votes + if (voted) 1 else -1)
            }
        }
        return Unit.right()
    }
}

/** Copies by naming the key it would have written, so a test can see what was asked for. */
internal class FakeFiles(private val failing: Boolean = false) : PlatformFileStore {
    val saved = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun save(
        pickedRef: String,
        directory: String,
        fileName: String,
    ): Either<DomainError, String> {
        if (failing) return DomainError.PersistenceFailure("no").left()
        val key = "$directory/$fileName.jpg"
        saved += key
        return key.right()
    }

    override suspend fun delete(storageKey: String) { deleted += storageKey }
    override suspend fun exists(storageKey: String) = true
    override suspend fun bytes(storageKey: String) = ByteArray(0).right()
    override suspend fun write(storageKey: String, bytes: ByteArray) = storageKey.right()
}

internal class CountingIds(private val prefix: String = "ticket") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${next++}"
}

internal fun telemetry(): SupportTelemetry = SupportTelemetry(
    logger = NoLogger,
    analytics = NoAnalytics,
    tracer = NoTracer,
)

private class NoSpan(
    override val spanId: String,
    override val traceId: String,
    override val parentSpanId: String?,
    override val name: String,
) : Span {
    override fun setAttribute(key: String, value: Any?): Span = this
}

private object NoTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        NoSpan("span", traceId, parentSpanId, name)

    override fun endSpan(span: Span) = Unit

    override fun flush() = Unit
}

private object NoLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

private object NoAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}
