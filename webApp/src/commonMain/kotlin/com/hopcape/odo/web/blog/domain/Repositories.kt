package com.hopcape.odo.web.blog.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.platform.UploadRequest
import com.hopcape.odo.web.blog.domain.model.Analytics
import com.hopcape.odo.web.blog.domain.model.Author
import com.hopcape.odo.web.blog.domain.model.Article
import com.hopcape.odo.web.blog.domain.model.AuthorPage
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.CategoryPage
import com.hopcape.odo.web.blog.domain.model.Draft
import com.hopcape.odo.web.blog.domain.model.IndexPage
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.domain.model.PostRow
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.domain.model.PublishOutcome
import com.hopcape.odo.web.blog.domain.model.SearchResults
import com.hopcape.odo.web.blog.domain.model.Session

/**
 * The seam.
 *
 * Everything above these interfaces — every ViewModel, every screen — is written
 * against them and has never heard of where the content lives. Today that is
 * `data/Sample*`, three objects holding the design's own copy. Tomorrow it is
 * Supabase, and the only files that change are the implementations and the one
 * line in [com.hopcape.odo.web.blog.di.blogModule] that names them.
 *
 * Three deliberate properties, each of which is what makes that swap cheap:
 *
 * - **Suspend, not Flow.** A blog is request/response. A flow would promise
 *   updates that no source here will ever push, and every screen would carry
 *   collection machinery for a stream that emits once.
 * - **`Either<WebError, T>`, not exceptions.** Same as the app's ports. A screen
 *   has to draw the failure, so the failure has to be a value it can branch on.
 * - **Page-shaped returns.** [index] hands back the lead story, the grid and the
 *   nav categories together, because that is one screen and it should be one
 *   round trip. Modelling it as three calls would work locally and cost three
 *   requests over a real connection.
 */
interface BlogRepository {

    /** The nav and the filter chips. Every public page draws these. */
    suspend fun categories(): Either<WebError, List<Category>>

    suspend fun index(): Either<WebError, IndexPage>

    /** [WebError.NotFound] when no post has that slug — the 404 page. */
    suspend fun article(slug: String): Either<WebError, Article>

    suspend fun category(slug: String): Either<WebError, CategoryPage>

    suspend fun author(slug: String): Either<WebError, AuthorPage>

    /** A blank [query] returns no hits and no suggestions, not everything. */
    suspend fun search(query: String): Either<WebError, SearchResults>

    /** What the 404 page offers instead. Most-read, not most-recent. */
    suspend fun mostRead(limit: Int): Either<WebError, List<PostSummary>>

    /** The footer's email capture on a thin category page. */
    suspend fun subscribe(email: String): Either<WebError, Unit>

    /**
     * "Want a topic that is not here?" on an empty search.
     *
     * Carries [query] as well as the address: a request with no idea what was
     * being looked for is a mailing-list signup, not a topic request.
     */
    suspend fun requestTopic(email: String, query: String): Either<WebError, Unit>
}

/** Who is signed in, and how they get that way. Only the CMS asks. */
interface AuthRepository {

    /** The current session, or null when signed out. Not an error either way. */
    suspend fun session(): Either<WebError, Session?>

    /** [WebError.SignInRejected] carries the countdown the design shows. */
    suspend fun signIn(email: String, password: String): Either<WebError, Session>

    suspend fun signOut(): Either<WebError, Unit>
}

/**
 * The CMS.
 *
 * Every call here assumes a session and returns [WebError.NotSignedIn] without
 * one, rather than trusting the router to have kept the reader out. The route
 * guard is a convenience; this is the check.
 */
interface AdminRepository {

    suspend fun posts(): Either<WebError, List<PostRow>>

    /** [id] is null for a post being started, which returns an empty [Draft]. */
    suspend fun draft(id: String?): Either<WebError, Draft>

    /** Returns the draft as stored — with an id, if this was its first save. */
    suspend fun save(draft: Draft): Either<WebError, Draft>

    /**
     * Publishes, or reports the slug is taken.
     *
     * [replaceExisting] is the design's second option on a conflict: the new post
     * takes the slug and the old one's traffic. Passing it is an explicit choice
     * by the author, never a retry the app makes on its own.
     */
    suspend fun publish(draft: Draft, replaceExisting: Boolean = false): Either<WebError, PublishOutcome>

    /** Back to draft. The URL stays alive, which is the point of not deleting. */
    suspend fun unpublish(id: String): Either<WebError, Unit>

    /**
     * Gone. Not a status change — the row goes.
     *
     * Only ever offered for a draft. A published post has a URL somebody may have
     * shared, and taking that to a 404 is what [unpublish] exists to avoid; a
     * draft has never been anywhere and deleting it costs nothing but the writing.
     */
    suspend fun discard(id: String): Either<WebError, Unit>

    suspend fun media(): Either<WebError, List<MediaItem>>

    suspend fun upload(file: UploadRequest): Either<WebError, MediaItem>

    suspend fun analytics(): Either<WebError, Analytics>

    /**
     * The signed-in author's own row.
     *
     * Not a copy of [Session] — that is who is signed in, this is what a reader
     * sees under a byline. They are the same person and different data, and the
     * second is the one an author edits.
     */
    suspend fun profile(): Either<WebError, Author>

    /** Saves the byline. The email is not editable: it is what the session is keyed on. */
    suspend fun saveProfile(name: String, bio: String, topics: String, since: String): Either<WebError, Author>
}
