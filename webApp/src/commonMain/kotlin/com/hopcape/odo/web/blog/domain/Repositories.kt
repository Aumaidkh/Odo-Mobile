package com.hopcape.odo.web.blog.domain

import arrow.core.Either
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
 * - **`Either<BlogError, T>`, not exceptions.** Same as the app's ports. A screen
 *   has to draw the failure, so the failure has to be a value it can branch on.
 * - **Page-shaped returns.** [index] hands back the lead story, the grid and the
 *   nav categories together, because that is one screen and it should be one
 *   round trip. Modelling it as three calls would work locally and cost three
 *   requests over a real connection.
 */
interface BlogRepository {

    /** The nav and the filter chips. Every public page draws these. */
    suspend fun categories(): Either<BlogError, List<Category>>

    suspend fun index(): Either<BlogError, IndexPage>

    /** [BlogError.NotFound] when no post has that slug — the 404 page. */
    suspend fun article(slug: String): Either<BlogError, Article>

    suspend fun category(slug: String): Either<BlogError, CategoryPage>

    suspend fun author(slug: String): Either<BlogError, AuthorPage>

    /** A blank [query] returns no hits and no suggestions, not everything. */
    suspend fun search(query: String): Either<BlogError, SearchResults>

    /** What the 404 page offers instead. Most-read, not most-recent. */
    suspend fun mostRead(limit: Int): Either<BlogError, List<PostSummary>>

    /** The footer's email capture on a thin category page. */
    suspend fun subscribe(email: String): Either<BlogError, Unit>

    /**
     * "Want a topic that is not here?" on an empty search.
     *
     * Carries [query] as well as the address: a request with no idea what was
     * being looked for is a mailing-list signup, not a topic request.
     */
    suspend fun requestTopic(email: String, query: String): Either<BlogError, Unit>
}

/** Who is signed in, and how they get that way. Only the CMS asks. */
interface AuthRepository {

    /** The current session, or null when signed out. Not an error either way. */
    suspend fun session(): Either<BlogError, Session?>

    /** [BlogError.SignInRejected] carries the countdown the design shows. */
    suspend fun signIn(email: String, password: String): Either<BlogError, Session>

    suspend fun signOut(): Either<BlogError, Unit>
}

/**
 * The CMS.
 *
 * Every call here assumes a session and returns [BlogError.NotSignedIn] without
 * one, rather than trusting the router to have kept the reader out. The route
 * guard is a convenience; this is the check.
 */
interface AdminRepository {

    suspend fun posts(): Either<BlogError, List<PostRow>>

    /** [id] is null for a post being started, which returns an empty [Draft]. */
    suspend fun draft(id: String?): Either<BlogError, Draft>

    /** Returns the draft as stored — with an id, if this was its first save. */
    suspend fun save(draft: Draft): Either<BlogError, Draft>

    /**
     * Publishes, or reports the slug is taken.
     *
     * [replaceExisting] is the design's second option on a conflict: the new post
     * takes the slug and the old one's traffic. Passing it is an explicit choice
     * by the author, never a retry the app makes on its own.
     */
    suspend fun publish(draft: Draft, replaceExisting: Boolean = false): Either<BlogError, PublishOutcome>

    /** Back to draft. The URL stays alive, which is the point of not deleting. */
    suspend fun unpublish(id: String): Either<BlogError, Unit>

    /**
     * Gone. Not a status change — the row goes.
     *
     * Only ever offered for a draft. A published post has a URL somebody may have
     * shared, and taking that to a 404 is what [unpublish] exists to avoid; a
     * draft has never been anywhere and deleting it costs nothing but the writing.
     */
    suspend fun discard(id: String): Either<BlogError, Unit>

    suspend fun media(): Either<BlogError, List<MediaItem>>

    suspend fun upload(file: UploadRequest): Either<BlogError, MediaItem>

    suspend fun analytics(): Either<BlogError, Analytics>

    /**
     * The signed-in author's own row.
     *
     * Not a copy of [Session] — that is who is signed in, this is what a reader
     * sees under a byline. They are the same person and different data, and the
     * second is the one an author edits.
     */
    suspend fun profile(): Either<BlogError, Author>

    /** Saves the byline. The email is not editable: it is what the session is keyed on. */
    suspend fun saveProfile(name: String, bio: String, topics: String, since: String): Either<BlogError, Author>
}

/**
 * An image on its way in.
 *
 * Carries the bytes rather than a browser `File` so the port stays common code —
 * reading the file is the browser's job and happens above this, at the one place
 * that already knows it is running in a browser.
 */
data class UploadRequest(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    // Data classes compare arrays by reference, which would make two identical
    // uploads unequal and one re-read of the same file equal to nothing.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is UploadRequest && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int =
        (name.hashCode() * 31 + mimeType.hashCode()) * 31 + bytes.contentHashCode()
}
