package com.hopcape.odo.web.blog.di

import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.PostImporter
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull

/**
 * The graph resolves.
 *
 * Worth its own test because a broken wire does not fail the build — it fails the
 * first time a screen asks for something, as a Koin exception whose message names
 * the thing that asked rather than the thing that was missing. Finding that from a
 * browser console is an afternoon; finding it here is a line number.
 */
class BlogModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun `every repository can be built`() {
        val koin = startKoin { modules(blogModule) }.koin

        assertNotNull(koin.get<BlogRepository>())
        assertNotNull(koin.get<AuthRepository>())
        assertNotNull(koin.get<AdminRepository>())
        assertNotNull(koin.get<PostImporter>())
    }

    @Test
    fun `the public client and the author client are not the same object`() {
        // The whole reason there are two. Shared, an author who signed in and then
        // browsed the blog read it as `authenticated`, and the author policy showed
        // them their own drafts on the public pages.
        val koin = startKoin { modules(blogModule) }.koin

        assertNotSame(
            koin.get<Postgrest>(named("anonymous")),
            koin.get<Postgrest>(named("author")),
            "the public side must not share a client with the CMS",
        )
    }
}
