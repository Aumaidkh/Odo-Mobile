package com.hopcape.odo.web.core.presentation.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text a ViewModel decided on, resolved where it is drawn.
 *
 * A ViewModel cannot call `stringResource` — that needs a composition — and it
 * must not hold a finished sentence either, or the copy for an error lives in
 * Kotlin instead of `strings.xml`. So it holds this: a reference the UI resolves
 * at the moment it draws.
 *
 * [Raw] exists for the one thing that is genuinely not copy — content. A post's
 * title comes from the author, not from the string table, and putting it through
 * a resource lookup would be pretending otherwise.
 *
 * A local copy of the app's `UiText` rather than a shared one, because no module
 * in this repo has a Wasm target yet. Same shape on purpose: if `:core:common`
 * ever grows one, this file is what gets deleted.
 */
@Immutable
sealed interface UiText {

    /** Content, not copy. Shown exactly as given. */
    @Immutable
    data class Raw(val value: String) : UiText

    /** Copy, from `strings.xml`. [args] fill its placeholders. */
    @Immutable
    data class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText
}

/** Resolves to a string. Only callable from a composition, which is the point. */
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
}

/** Shorthand for the common case at a call site that already has the resource. */
fun StringResource.asUiText(vararg args: Any): UiText = UiText.Resource(this, args.toList())
