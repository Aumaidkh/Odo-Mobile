package com.hopcape.odo.web.blog.presentation

import com.hopcape.odo.web.blog.data.SampleContent
import com.hopcape.odo.web.blog.di.blogModule
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The path from a clicked row to a loaded editor.
 *
 * Every piece of it was already proven on its own — the router builds the right
 * URL, the repository returns the right draft — and the editor still opened
 * blank. What was left untested was the join: the route argument travelling
 * through Koin into the ViewModel, and the ViewModel putting what it read on
 * screen. So that is what this covers.
 */
class EditorWiringTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin { modules(blogModule) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `a slug survives the trip through DI into the editor`() = runTest(dispatcher) {
        val koin = org.koin.core.context.GlobalContext.get()
        koin.get<AuthRepository>().signIn(SampleContent.SIGN_IN_EMAIL, SampleContent.SIGN_IN_PASSWORD)

        val slug = "challan-kaise-check-karein"
        val viewModel = koin.get<EditorViewModel> { parametersOf(slug) }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            "Challan kaise check karein — poori guide",
            state.title,
            "the editor opened a different post than the row that was clicked",
        )
        assertTrue(state.blocks.isNotEmpty(), "the body did not arrive with the draft")
        assertEquals(slug, state.seo.slug)
    }

    @Test
    fun `a post with no id opens empty`() = runTest(dispatcher) {
        val koin = org.koin.core.context.GlobalContext.get()
        koin.get<AuthRepository>().signIn(SampleContent.SIGN_IN_EMAIL, SampleContent.SIGN_IN_PASSWORD)

        val viewModel = koin.get<EditorViewModel> { parametersOf(null) }
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.title)
        assertTrue(viewModel.state.value.blocks.isEmpty())
    }
}
