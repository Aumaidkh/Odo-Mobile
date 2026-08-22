package com.hopcape.odo.web.blog.domain

import com.hopcape.odo.web.blog.domain.model.ArticleBlock

/**
 * A post that arrived as text rather than as typing.
 *
 * Every field is optional except the body, because the two things somebody
 * pastes are different: a whole post exported from somewhere, or just the blocks
 * of one article. What is absent is left alone in the editor rather than cleared.
 */
data class ImportedPost(
    val title: String?,
    val dek: String?,
    val slug: String?,
    val body: List<ArticleBlock>,
)

/**
 * Reads a post out of pasted JSON.
 *
 * A port, so the editor does not have to know the wire format. The shape it
 * accepts is the shape the database stores — that is the point of importing at
 * all: moving a post between environments, or drafting one somewhere else,
 * without a converter in the middle that has to be kept in step.
 *
 * Null means the text was not a post. Not an exception: an author pasting the
 * wrong thing is an ordinary Tuesday, and it has to be drawable.
 */
interface PostImporter {
    fun parse(json: String): ImportedPost?
}
