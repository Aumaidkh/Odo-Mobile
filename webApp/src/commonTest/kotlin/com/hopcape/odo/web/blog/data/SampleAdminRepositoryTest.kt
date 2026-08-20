package com.hopcape.odo.web.blog.data

import com.hopcape.odo.web.blog.domain.BlogError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The CMS repository, which is the half of the editor that can be tested without
 * a browser in the way.
 */
class SampleAdminRepositoryTest {

    private suspend fun signedIn(): SampleAdminRepository {
        val auth = SampleAuthRepository()
        auth.signIn(SampleContent.SIGN_IN_EMAIL, SampleContent.SIGN_IN_PASSWORD)
        return SampleAdminRepository(auth)
    }

    @Test
    fun `every admin call needs a session`() = runTest {
        val admin = SampleAdminRepository(SampleAuthRepository())
        assertEquals(BlogError.NotSignedIn, admin.posts().leftOrNull())
        assertEquals(BlogError.NotSignedIn, admin.draft(null).leftOrNull())
        assertEquals(BlogError.NotSignedIn, admin.analytics().leftOrNull())
    }

    @Test
    fun `a published post opens with its title and body`() = runTest {
        val admin = signedIn()
        val draft = admin.draft("how-to-check-challans").getOrNull()
        assertNotNull(draft, "the post the design's table links to has to open")
        assertEquals("How to check your challans — the full guide", draft.title)
        assertTrue(draft.body.isNotEmpty(), "an opened post has to arrive with its body")
        assertEquals("how-to-check-challans", draft.seo.slug)
    }

    @Test
    fun `a post that was never started has no id`() = runTest {
        val draft = signedIn().draft(null).getOrNull()
        assertNotNull(draft)
        assertNull(draft.id)
        assertEquals("", draft.title)
    }

    @Test
    fun `an unknown id is not found`() = runTest {
        assertEquals(BlogError.NotFound, signedIn().draft("nothing-here").leftOrNull())
    }

    @Test
    fun `every row in the table can be opened`() = runTest {
        val admin = signedIn()
        val rows = admin.posts().getOrNull().orEmpty()
        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            assertNotNull(
                admin.draft(row.id).getOrNull(),
                "row '${row.title}' has id '${row.id}', which opens nothing",
            )
        }
    }

    @Test
    fun `publishing onto a taken slug reports who has it`() = runTest {
        val admin = signedIn()
        val fresh = admin.draft(null).getOrNull()!!
        val outcome = admin.publish(
            fresh.copy(title = "Something else", seo = fresh.seo.copy(slug = "expired-puc")),
        ).getOrNull()
        assertTrue(
            outcome is com.hopcape.odo.web.blog.domain.model.PublishOutcome.SlugTaken,
            "publishing onto a live URL has to be a choice, not a silent overwrite",
        )
    }
}
