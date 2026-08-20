package com.hopcape.odo.web.blog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.BlogRepository
import com.hopcape.odo.web.blog.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The header's categories.
 *
 * Scoped to the page rather than to a route, because the header outlives every
 * screen under it: reading the list once per page load instead of once per
 * navigation is the difference between a nav that is simply there and one that
 * appears a moment after each page does.
 *
 * A failure is silent on purpose. If the categories cannot be read, the nav
 * renders without them and the page below still works — an error banner across
 * the top of an article the reader can see would be worse than a shorter nav.
 */
class ChromeViewModel(
    private val blog: BlogRepository,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            _categories.value = blog.categories().getOrNull().orEmpty()
        }
    }
}
