package com.hopcape.odo.feature.profile.presentation

import androidx.lifecycle.ViewModel
import com.hopcape.odo.core.config.ConfigResolver
import com.hopcape.odo.core.config.LocalConfigOverrides
import com.hopcape.odo.core.config.ResolvedConfigValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The debug config screen's state.
 *
 * [overrides] is nullable because a release build has no store behind it. The screen is
 * unreachable there, so this is belt and braces rather than a case that happens — but it
 * means the class cannot be the thing that makes a release build crash.
 */
internal class ConfigOverridesViewModel(
    private val resolver: ConfigResolver,
    private val overrides: LocalConfigOverrides?,
) : ViewModel() {

    private val _keys = MutableStateFlow(resolver.describeAll())
    val keys: StateFlow<List<ResolvedConfigValue>> = _keys.asStateFlow()

    val editable: Boolean = overrides != null

    fun set(key: String, raw: String) {
        overrides?.set(key, raw)
        refresh()
    }

    fun clear(key: String) {
        overrides?.clear(key)
        refresh()
    }

    fun clearAll() {
        overrides?.clearAll()
        refresh()
    }

    /**
     * Re-reads every key rather than the one that changed. The list is short, a read is a
     * map lookup, and a rejected override — a value that does not parse, or falls outside
     * its range — has to show the value that actually won instead of the one just typed.
     */
    private fun refresh() {
        _keys.value = resolver.describeAll()
    }
}
