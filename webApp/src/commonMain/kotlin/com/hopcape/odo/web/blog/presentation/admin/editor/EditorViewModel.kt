package com.hopcape.odo.web.blog.presentation.admin.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.PostImporter
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.Draft
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.domain.model.PublishOutcome
import com.hopcape.odo.web.blog.domain.model.SeoDraft
import com.hopcape.odo.web.blog.presentation.asUiText
import com.hopcape.odo.web.blog.presentation.isRetryable
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.UiText
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_import_unreadable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * What is on top of the editor.
 *
 * One value, not a boolean each, because they are mutually exclusive and the
 * design treats them that way — a slug conflict replaces the publish sheet, it
 * does not stack on top of it.
 */
@Immutable
sealed interface EditorSheet {
    data object None : EditorSheet

    /** The publish and SEO form. */
    data object Publish : EditorSheet

    /** The slug is taken. Carries who has it and what to use instead. */
    @Immutable
    data class Conflict(val outcome: PublishOutcome.SlugTaken) : EditorSheet

    /** It went live. Carries the URL, for the copy button. */
    @Immutable
    data class Published(val slug: String) : EditorSheet

    /** Leaving with work that is not saved. */
    data object Unsaved : EditorSheet

    /** The media library, to place an image. */
    data object InsertImage : EditorSheet

    /** Paste a post in as JSON. */
    data object Import : EditorSheet

    /** Picking which category this post is filed under. */
    data object Category : EditorSheet

    /** Throwing a draft away for good. */
    data object Discard : EditorSheet

    /** Taking a published post back to draft. */
    @Immutable
    data class Unpublish(val views: Int?) : EditorSheet
}

sealed interface EditorEvent {
    data class TitleChanged(val value: String) : EditorEvent
    data class BlockChanged(val index: Int, val text: String) : EditorEvent
    data class BlockAdded(val kind: BlockKind) : EditorEvent
    data class BlockRemoved(val index: Int) : EditorEvent

    /** One field of one action card. Its own event because it is not "the text". */
    data class ShowcaseFieldChanged(
        val index: Int,
        val field: ShowcaseField,
        val value: String,
    ) : EditorEvent

    data object SaveTapped : EditorEvent
    data object PublishTapped : EditorEvent
    data object UnpublishTapped : EditorEvent
    data object InsertImageTapped : EditorEvent
    data object ImportTapped : EditorEvent
    data object CategoryTapped : EditorEvent
    data object DiscardTapped : EditorEvent
    data object SheetDismissed : EditorEvent

    /** The pasted JSON. Parsed here, because a parse failure is a state to draw. */
    data class Imported(val json: String) : EditorEvent

    data object DiscardConfirmed : EditorEvent

    data class SeoTitleChanged(val value: String) : EditorEvent
    data class SlugChanged(val value: String) : EditorEvent
    data class MetaChanged(val value: String) : EditorEvent
    data class CategoryChanged(val slug: String) : EditorEvent

    data object PublishConfirmed : EditorEvent

    /** From the conflict sheet: take the suggested slug, or take the URL over. */
    data class ConflictResolved(val replaceExisting: Boolean) : EditorEvent

    data object UnpublishConfirmed : EditorEvent
    data class ImageChosen(val item: MediaItem) : EditorEvent

    /** The reader is leaving; the host asks first so unsaved work can be caught. */
    data object LeaveRequested : EditorEvent
    data object LeaveConfirmed : EditorEvent
}

sealed interface EditorEffect {
    /** Sign-off: the host navigates away once the editor has agreed to it. */
    data object Leave : EditorEffect

    data class CopyUrl(val url: String) : EditorEffect
    data class OpenPost(val slug: String) : EditorEffect
}

@Immutable
data class EditorUiState(
    val loaded: Loadable<Draft>,
    val title: String,
    val blocks: List<ArticleBlock>,
    val seo: SeoDraft,
    val status: PostStatus,
    /** True when there are edits the repository has not been told about. */
    val dirty: Boolean,
    val saving: Boolean,
    /** True once anything has been saved at all — the design's "not saved" state. */
    val everSaved: Boolean,
    val sheet: EditorSheet,
    val categories: List<Category>,
    val media: List<MediaItem>,
    val error: UiText?,
    /**
     * Bumped only when the body is replaced from outside — a load, or an import.
     *
     * The editor's fields hold text the stored model deliberately does not: an
     * empty `****` that **B** just opened parses to no runs at all, which is right
     * for storage and wrong for a caret sitting between them. So the fields are
     * seeded once and left alone, and this is the signal that says re-read.
     */
    val revision: Int = 0,
) {
    val wordCount: Int
        get() = blocks.sumOf { block -> block.editableText().split(' ', '\n').count { it.isNotBlank() } }

    /** 200 words a minute, floor of one — the same estimate the reader is shown. */
    val readingMinutes: Int get() = maxOf(1, (wordCount + 199) / 200)

    val seoTitleOverLimit: Boolean get() = seo.seoTitle.length > SeoDraft.TITLE_LIMIT
    val metaOverLimit: Boolean get() = seo.metaDescription.length > SeoDraft.DESCRIPTION_LIMIT

    /** A post with no title is not a post. Everything else can be filled in later. */
    val canPublish: Boolean get() = title.isNotBlank() && !saving
}

/**
 * The editor.
 *
 * [postId] is null for a post being started, which is a distinct state rather
 * than an empty string: it is the one moment when leaving the page loses work,
 * and the design draws it as "New post · not saved".
 *
 * Saving is explicit. Autosave would be kinder, but it needs a debounce, a clock
 * and a story for what happens when the save fails while somebody is still
 * typing — and a Save button that works is better than an autosave that silently
 * does not.
 */
class EditorViewModel(
    private val postId: String?,
    private val admin: AdminRepository,
    private val blog: BlogRepository,
    private val importer: PostImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditorUiState(
            loaded = Loadable.Loading,
            title = "",
            blocks = emptyList(),
            seo = SeoDraft(),
            status = PostStatus.DRAFT,
            dirty = false,
            saving = false,
            everSaved = postId != null,
            sheet = EditorSheet.None,
            categories = emptyList(),
            media = emptyList(),
            error = null,
        ),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    init {
        load()
        // The category picker and the media library are both small, fixed lists
        // that the sheets need the instant they open. Reading them with the draft
        // costs one round trip and removes a spinner from inside a sheet.
        //
        // Each read finishes into a local before the state is touched. Written the
        // short way — `_state.value = _state.value.copy(x = read())` — Kotlin
        // evaluates `_state.value` first and only then suspends in the argument, so
        // the write lands on a snapshot taken before the wait and silently undoes
        // whatever else finished during it. That is what emptied the editor: three
        // reads racing, and the slowest one restoring a state from before the draft
        // had arrived.
        viewModelScope.launch {
            val categories = blog.categories().getOrNull().orEmpty()
            _state.value = _state.value.copy(categories = categories)
        }
        viewModelScope.launch {
            val media = admin.media().getOrNull().orEmpty()
            _state.value = _state.value.copy(media = media)
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.TitleChanged -> edit { copy(title = event.value) }

            is EditorEvent.BlockChanged -> edit {
                copy(
                    blocks = blocks.mapIndexed { index, block ->
                        if (index == event.index) block.withText(event.text) else block
                    },
                )
            }

            is EditorEvent.ShowcaseFieldChanged -> edit {
                copy(
                    blocks = blocks.mapIndexed { index, block ->
                        if (index == event.index && block is ArticleBlock.AppShowcase) {
                            block.withField(event.field, event.value)
                        } else {
                            block
                        }
                    },
                )
            }

            is EditorEvent.BlockAdded -> edit { copy(blocks = blocks + event.kind.empty()) }

            is EditorEvent.BlockRemoved -> edit {
                copy(blocks = blocks.filterIndexed { index, _ -> index != event.index })
            }

            is EditorEvent.SeoTitleChanged -> edit { copy(seo = seo.copy(seoTitle = event.value)) }
            is EditorEvent.SlugChanged -> edit { copy(seo = seo.copy(slug = event.value)) }
            is EditorEvent.MetaChanged -> edit { copy(seo = seo.copy(metaDescription = event.value)) }
            is EditorEvent.CategoryChanged -> edit { copy(seo = seo.copy(categorySlug = event.slug)) }

            EditorEvent.SaveTapped -> save(thenPublish = false)

            EditorEvent.PublishTapped -> _state.value = _state.value.copy(
                sheet = EditorSheet.Publish,
                // Seed the SEO fields from what has been written, so the sheet
                // opens filled in rather than asking for the title twice.
                seo = _state.value.seo.seeded(_state.value.title),
            )

            EditorEvent.UnpublishTapped -> _state.value =
                _state.value.copy(sheet = EditorSheet.Unpublish(views = null))

            EditorEvent.InsertImageTapped -> _state.value =
                _state.value.copy(sheet = EditorSheet.InsertImage)

            EditorEvent.ImportTapped -> _state.value = _state.value.copy(sheet = EditorSheet.Import)
            EditorEvent.CategoryTapped -> _state.value = _state.value.copy(sheet = EditorSheet.Category)
            EditorEvent.DiscardTapped -> _state.value = _state.value.copy(sheet = EditorSheet.Discard)

            is EditorEvent.Imported -> import(event.json)
            EditorEvent.DiscardConfirmed -> discard()

            EditorEvent.SheetDismissed -> _state.value = _state.value.copy(sheet = EditorSheet.None)

            EditorEvent.PublishConfirmed -> publish(replaceExisting = false)
            is EditorEvent.ConflictResolved -> resolveConflict(event.replaceExisting)
            EditorEvent.UnpublishConfirmed -> unpublish()

            is EditorEvent.ImageChosen -> edit {
                copy(
                    // Placed as a showcase block: the only image the design puts in
                    // a body is the app, in its own card, with a call to action.
                    blocks = blocks + ArticleBlock.AppShowcase(
                        heading = "",
                        body = "",
                        callToAction = "Download Odo",
                        screenshot = event.item.url,
                    ),
                    sheet = EditorSheet.None,
                )
            }

            // Leaving is a request, not an action. The host asks; the editor
            // decides whether there is anything to lose first.
            EditorEvent.LeaveRequested ->
                if (_state.value.dirty) {
                    _state.value = _state.value.copy(sheet = EditorSheet.Unsaved)
                } else {
                    viewModelScope.launch { _effects.send(EditorEffect.Leave) }
                }

            EditorEvent.LeaveConfirmed -> {
                _state.value = _state.value.copy(sheet = EditorSheet.None, dirty = false)
                viewModelScope.launch { _effects.send(EditorEffect.Leave) }
            }
        }
    }

    /** Every edit marks the draft dirty. One place, so none of them can forget. */
    private fun edit(change: EditorUiState.() -> EditorUiState) {
        _state.value = _state.value.change().copy(dirty = true, error = null)
    }

    /**
     * Reads the draft straight into the state.
     *
     * Deliberately not a second flow mirrored into this one. That is what it was,
     * and the mirror is why the editor drew an empty post while every layer under
     * it held the right one: two places claiming to know the draft, updated by two
     * coroutines, and the screen reading whichever had been written last.
     */
    private fun load() {
        _state.value = _state.value.copy(loaded = Loadable.Loading)
        viewModelScope.launch {
            admin.draft(postId).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(
                        loaded = Loadable.Failed(error.asUiText(), error.isRetryable, error),
                    )
                },
                ifRight = { draft ->
                    _state.value = _state.value.copy(
                        loaded = Loadable.Ready(draft),
                        title = draft.title,
                        blocks = draft.body,
                        seo = draft.seo,
                        status = draft.status,
                        revision = _state.value.revision + 1,
                    )
                },
            )
        }
    }

    private fun save(thenPublish: Boolean) {
        val current = _state.value
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            admin.save(current.asDraft(postId)).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(saving = false, error = error.asUiText())
                },
                ifRight = { saved ->
                    _state.value = _state.value.copy(
                        saving = false,
                        dirty = false,
                        everSaved = true,
                        loaded = Loadable.Ready(saved),
                    )
                    if (thenPublish) publish(replaceExisting = false)
                },
            )
        }
    }

    private fun publish(replaceExisting: Boolean) {
        val current = _state.value
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            admin.publish(current.asDraft(postId), replaceExisting).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(saving = false, error = error.asUiText())
                },
                ifRight = { outcome ->
                    _state.value = when (outcome) {
                        is PublishOutcome.Published -> _state.value.copy(
                            saving = false,
                            dirty = false,
                            everSaved = true,
                            status = PostStatus.PUBLISHED,
                            sheet = EditorSheet.Published(outcome.slug),
                        )

                        is PublishOutcome.SlugTaken -> _state.value.copy(
                            saving = false,
                            sheet = EditorSheet.Conflict(outcome),
                        )
                    }
                },
            )
        }
    }

    /**
     * Takes a whole post from pasted JSON.
     *
     * The shape it accepts is the shape the database stores, so a post can be
     * moved between environments — or written somewhere else entirely — without a
     * converter in the middle. A bare array is read as just the body, because that
     * is what somebody copying one article's blocks will have on their clipboard.
     *
     * Nothing is saved. The import lands in the editor and the author reads it
     * before deciding, which is the difference between an import and an overwrite.
     */
    private fun import(json: String) {
        val imported = importer.parse(json)
        if (imported == null) {
            _state.value = _state.value.copy(error = UiText.Resource(Res.string.bl_import_unreadable))
            return
        }
        _state.value = _state.value.copy(
            title = imported.title ?: _state.value.title,
            blocks = imported.body,
            seo = _state.value.seo.copy(
                seoTitle = imported.title ?: _state.value.seo.seoTitle,
                slug = imported.slug ?: _state.value.seo.slug,
                metaDescription = imported.dek ?: _state.value.seo.metaDescription,
            ),
            sheet = EditorSheet.None,
            dirty = true,
            error = null,
            revision = _state.value.revision + 1,
        )
    }

    /**
     * Throws the draft away.
     *
     * Only reachable for a post that has been saved at least once — there is
     * nothing to delete before that, and the host leaves the editor either way.
     */
    private fun discard() {
        val id = postId
        if (id == null) {
            _state.value = _state.value.copy(sheet = EditorSheet.None, dirty = false)
            viewModelScope.launch { _effects.send(EditorEffect.Leave) }
            return
        }
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            admin.discard(id).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(saving = false, sheet = EditorSheet.None, error = error.asUiText())
                },
                ifRight = {
                    _state.value = _state.value.copy(saving = false, dirty = false, sheet = EditorSheet.None)
                    _effects.send(EditorEffect.Leave)
                },
            )
        }
    }

    /**
     * The two ways out of a slug conflict.
     *
     * Taking the suggested slug is a plain retry with a different value. Replacing
     * is not — it takes a live URL away from a post that is earning traffic on it,
     * so it only ever happens because somebody chose it in the sheet.
     */
    private fun resolveConflict(replaceExisting: Boolean) {
        val conflict = (_state.value.sheet as? EditorSheet.Conflict) ?: return
        if (!replaceExisting) {
            _state.value = _state.value.copy(
                seo = _state.value.seo.copy(slug = conflict.outcome.suggestion),
                sheet = EditorSheet.Publish,
            )
        } else {
            publish(replaceExisting = true)
        }
    }

    private fun unpublish() {
        val id = postId ?: return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            admin.unpublish(id).fold(
                ifLeft = { error -> _state.value = _state.value.copy(saving = false, error = error.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(
                        saving = false,
                        status = PostStatus.DRAFT,
                        sheet = EditorSheet.None,
                    )
                },
            )
        }
    }

    fun copyUrl(slug: String) {
        viewModelScope.launch { _effects.send(EditorEffect.CopyUrl("https://odoapp.in/blog/$slug")) }
    }

    fun openPost(slug: String) {
        viewModelScope.launch { _effects.send(EditorEffect.OpenPost(slug)) }
    }
}

/** The screen's working copy, as the thing the repository stores. */
private fun EditorUiState.asDraft(id: String?): Draft = Draft(
    id = id,
    title = title,
    body = blocks,
    status = status,
    seo = seo,
    wordCount = wordCount,
    readingMinutes = readingMinutes,
)

/**
 * Fills the SEO fields in from the post the first time the sheet opens.
 *
 * Only what is empty. Overwriting a slug somebody chose deliberately, because
 * they later edited the title, is how a shared URL quietly stops working.
 */
private fun SeoDraft.seeded(title: String): SeoDraft = copy(
    seoTitle = seoTitle.ifBlank { title },
    slug = slug.ifBlank { title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') },
)
