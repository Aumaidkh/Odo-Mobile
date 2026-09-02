package com.hopcape.odo.feature.questionnaire

import arrow.core.Either
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.feature.questionnaire.presentation.QuestionnaireTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Confirms [questionnaireModule] resolves end to end.
 *
 * The ViewModel is deliberately not resolved here: it needs a `ViewModelStoreOwner` that only
 * a Compose entry supplies, and its route argument arrives through `parametersOf`. What this
 * guards is the wiring around it — that the entry provider is bound to [FeatureEntryProvider]
 * so the host's `getAll` finds it, which is the failure that would otherwise be silent.
 */
class QuestionnaireModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    /**
     * Two providers: the registry-driven questionnaire screen, and first-run setup. Both must
     * survive `getAll`, which is what `bind FeatureEntryProvider::class` buys — registering
     * them as `single<FeatureEntryProvider>` would silently leave one.
     */
    @Test
    fun bothEntryProvidersAreBoundSoTheHostCollectsThem() {
        val koin = graph()

        val providers = koin.getAll<FeatureEntryProvider>()

        assertEquals(2, providers.size, "found: ${'$'}{providers.map { it::class.simpleName }}")
    }

    @Test
    fun theRegistryResolves() {
        val koin = graph()

        assertIs<QuestionRegistry>(koin.get<QuestionRegistry>())
    }

    @Test
    fun theTelemetryFacadeResolves() {
        val koin = graph()

        assertIs<QuestionnaireTelemetry>(koin.get<QuestionnaireTelemetry>())
    }

    /** Stands in for the ports other modules publish. */
    private fun graph() = koinApplication {
        modules(
            questionnaireModule,
            module {
                single<NavigationManager> { NavigationManager() }
                single<QuestionnaireRepository> { NoopRepository() }
                single<Logger> { NoopLogger() }
                single<AnalyticsTracker> { NoopAnalytics() }
            },
        )
    }.koin

    private class NoopRepository : QuestionnaireRepository {
        override suspend fun save(key: QuestionKey, values: Set<String>): Either<DomainError, Unit> =
            Unit.right()

        override fun observe(): Flow<List<QuestionAnswer>> = flowOf(emptyList())

        override suspend fun answersFor(key: QuestionKey): Either<DomainError, List<QuestionAnswer>> =
            emptyList<QuestionAnswer>().right()
    }

    private class NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }

    private class NoopAnalytics : AnalyticsTracker {
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
