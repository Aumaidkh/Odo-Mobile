package com.hopcape.odo.core.domain.settings.model

/**
 * Which palette the app draws itself in.
 *
 * [SYSTEM] follows the device setting, which is what an owner gets until they choose —
 * an app that ignores a phone in dark mode reads as broken rather than as styled.
 */
enum class ThemePreference {
    DARK,
    LIGHT,
    SYSTEM,
}
