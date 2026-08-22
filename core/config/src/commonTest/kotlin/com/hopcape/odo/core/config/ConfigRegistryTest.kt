package com.hopcape.odo.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigRegistryTest {

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

    @Test
    fun `defaults expose every compiled value for the SDK to be seeded with`() {
        val registry = ConfigRegistry(listOf(SampleConfigContribution))

        assertEquals(
            mapOf(
                SampleConfigContribution.ENABLED to true,
                SampleConfigContribution.RETRY_COUNT to 3,
                // The canonical constant name, not the lower-case form a console holds.
                // Seeding the SDK with this is safe because reads match ignoring case.
                SampleConfigContribution.MODE to "OFF",
            ),
            registry.defaults(),
        )
    }

    @Test
    fun `a key declared by two modules is recorded and the first one wins`() {
        // KSP validates one module at a time, so this is the only place a
        // cross-module clash can be seen at all.
        val registry = ConfigRegistry(listOf(SampleConfigContribution, ClashingContribution))

        assertEquals(listOf(SampleConfigContribution.ENABLED), registry.duplicateKeys)
        assertEquals(true, registry.require(SampleConfigContribution.ENABLED).default)
    }

    @Test
    fun `a registry with no clashes reports none`() {
        assertEquals(emptyList(), ConfigRegistry(listOf(SampleConfigContribution)).duplicateKeys)
    }

    @Test
    fun `an unregistered key names the likely cause`() {
        val failure = assertFailsWith<IllegalStateException> {
            ConfigRegistry(emptyList()).require("sample_enabled")
        }
        assertTrue(failure.message.orEmpty().contains("Koin module"))
    }
}
