package com.hopcape.odo.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DuplicateKeysTest {

    private object ClashingContribution : ConfigContribution {
        override val groupName: String = "clashing"
        override val keys: List<ConfigKey> = listOf(
            ConfigKey(
                key = SampleConfigContribution.ENABLED,
                type = ConfigType.BOOLEAN,
                default = false,
                owner = "someone else",
                why = "Declared the same key from another module",
            ),
        )
    }

    private fun registry(vararg contributions: ConfigContribution) = ConfigRegistry(contributions.toList())

    @Test
    fun `a clash fails the build's own launch in debug`() {
        // Loud on a developer's machine is the only place this gets noticed. One module's
        // default silently loses otherwise, and every consumer of the losing declaration
        // reads a value nobody wrote for it.
        val failure = assertFailsWith<IllegalStateException> {
            enforceUniqueKeys(
                registry = registry(SampleConfigContribution, ClashingContribution),
                isDebug = true,
                onWarn = { error("should not warn in debug") },
            )
        }

        assertTrue(failure.message.orEmpty().contains(SampleConfigContribution.ENABLED))
    }

    @Test
    fun `a clash only logs in release`() {
        // The wrong default is survivable. A crash on launch is not.
        val warnings = mutableListOf<String>()

        enforceUniqueKeys(
            registry = registry(SampleConfigContribution, ClashingContribution),
            isDebug = false,
            onWarn = warnings::add,
        )

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains(SampleConfigContribution.ENABLED))
    }

    @Test
    fun `no clash does nothing in either build`() {
        enforceUniqueKeys(registry(SampleConfigContribution), isDebug = true) { error("no warning expected") }
        enforceUniqueKeys(registry(SampleConfigContribution), isDebug = false) { error("no warning expected") }
    }
}
