package com.hopcape.odo.core.config.processor

import kotlin.test.Test

class ConfigProcessorTest {

    // ── What it generates ─────────────────────────────────────────────────────

    @Test
    fun `a group generates an impl, flows, a contribution and a Koin module`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Flag
            import com.hopcape.odo.core.config.Value

            enum class Mode { OFF, DEGRADED, ON }

            @ConfigGroup("sample")
            interface SampleConfig {
                @Flag(key = "sample_enabled", default = true, owner = "platform", why = "why")
                val enabled: Boolean

                @Value(key = "sample_retries", default = "3", owner = "platform", why = "why", range = "0..10")
                val retries: Int

                @Value(key = "sample_mode", default = "off", owner = "platform", why = "why")
                val mode: Mode
            }
            """.trimIndent(),
        )

        result.assertGeneratedContains(
            "public class SampleConfigImpl",
            "public class SampleConfigFlows",
            "public object SampleConfigContribution",
            "public val sampleConfigModule",
        )
    }

    @Test
    fun `the generated Koin binding keeps a distinct primary type`() {
        // single<ConfigContribution> { … } would compile and silently lose every group
        // but one, because in Koin a second definition of the same type replaces the
        // first. The house pattern binds a distinct primary type instead.
        val result = process(simpleGroup())

        result.assertGeneratedContains(
            "single { SampleConfigContribution } bind ConfigContribution::class",
        )
    }

    @Test
    fun `a lowercase enum default is canonicalised to the constant name`() {
        // A remote console holds "off"; the Kotlin constant is OFF. The generated code
        // calls valueOf, so the stored default has to be the constant name.
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            enum class Mode { OFF, ON }

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_mode", default = "off", owner = "platform", why = "why")
                val mode: Mode
            }
            """.trimIndent(),
        )

        result.assertGeneratedContains("""default = "OFF"""", """enumValues = listOf("OFF", "ON")""")
    }

    @Test
    fun `a key with no range omits it from the descriptor`() {
        process(simpleGroup()).assertGeneratedContains("owner = \"platform\"")
    }

    // ── What it rejects ───────────────────────────────────────────────────────

    @Test
    fun `a key that breaks the naming rule is rejected`() {
        process(group(key = "Sample_Enabled")).assertRejected("enabled", "Sample_Enabled")
    }

    @Test
    fun `two keys with the same name in one module are rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Flag

            @ConfigGroup("first")
            interface FirstConfig {
                @Flag(key = "shared_key", default = true, owner = "platform", why = "why")
                val one: Boolean
            }

            @ConfigGroup("second")
            interface SecondConfig {
                @Flag(key = "shared_key", default = false, owner = "platform", why = "why")
                val two: Boolean
            }
            """.trimIndent(),
        )

        result.assertRejected("shared_key", "declared twice")
    }

    @Test
    fun `an unsupported property type is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_list", default = "", owner = "platform", why = "why")
                val items: List<String>
            }
            """.trimIndent(),
        )

        result.assertRejected("items", "unsupported type")
    }

    @Test
    fun `a nullable property is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_text", default = "", owner = "platform", why = "why")
                val text: String?
            }
            """.trimIndent(),
        )

        result.assertRejected("text", "nullable")
    }

    @Test
    fun `a default that does not match the declared type is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_retries", default = "three", owner = "platform", why = "why")
                val retries: Int
            }
            """.trimIndent(),
        )

        result.assertRejected("retries", "three", "not a int")
    }

    @Test
    fun `a range that does not parse is rejected`() {
        process(valueGroup(default = "3", range = "0 to 10"))
            .assertRejected("retries", "0 to 10")
    }

    @Test
    fun `a default outside its range is rejected`() {
        process(valueGroup(default = "99", range = "0..10"))
            .assertRejected("retries", "99", "outside its range")
    }

    @Test
    fun `a range on a non-number is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_text", default = "a", owner = "platform", why = "why", range = "0..10")
                val text: String
            }
            """.trimIndent(),
        )

        result.assertRejected("text", "not a number")
    }

    @Test
    fun `an enum default that is not one of the constants is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            enum class Mode { OFF, ON }

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_mode", default = "sideways", owner = "platform", why = "why")
                val mode: Mode
            }
            """.trimIndent(),
        )

        result.assertRejected("mode", "sideways", "OFF")
    }

    @Test
    fun `a Flag on a non-boolean is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Flag

            @ConfigGroup("sample")
            interface SampleConfig {
                @Flag(key = "sample_retries", default = true, owner = "platform", why = "why")
                val retries: Int
            }
            """.trimIndent(),
        )

        result.assertRejected("retries", "not a Boolean")
    }

    @Test
    fun `a Value on a boolean is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup
            import com.hopcape.odo.core.config.Value

            @ConfigGroup("sample")
            interface SampleConfig {
                @Value(key = "sample_enabled", default = "true", owner = "platform", why = "why")
                val enabled: Boolean
            }
            """.trimIndent(),
        )

        result.assertRejected("enabled", "use @Flag")
    }

    @Test
    fun `a property with no annotation at all is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup

            @ConfigGroup("sample")
            interface SampleConfig {
                val forgotten: Boolean
            }
            """.trimIndent(),
        )

        result.assertRejected("forgotten", "neither @Flag nor @Value")
    }

    @Test
    fun `a missing owner or why is rejected`() {
        process(group(owner = " ")).assertRejected("enabled", "owner")
    }

    @Test
    fun `ConfigGroup on something that is not an interface is rejected`() {
        val result = process(
            """
            package sample

            import com.hopcape.odo.core.config.ConfigGroup

            @ConfigGroup("sample")
            class SampleConfig
            """.trimIndent(),
        )

        result.assertRejected("only be applied to an interface")
    }

    // ── snippets ──────────────────────────────────────────────────────────────

    private fun simpleGroup() = group()

    private fun group(key: String = "sample_enabled", owner: String = "platform") =
        """
        package sample

        import com.hopcape.odo.core.config.ConfigGroup
        import com.hopcape.odo.core.config.Flag

        @ConfigGroup("sample")
        interface SampleConfig {
            @Flag(key = "$key", default = true, owner = "$owner", why = "why")
            val enabled: Boolean
        }
        """.trimIndent()

    private fun valueGroup(default: String, range: String) =
        """
        package sample

        import com.hopcape.odo.core.config.ConfigGroup
        import com.hopcape.odo.core.config.Value

        @ConfigGroup("sample")
        interface SampleConfig {
            @Value(key = "sample_retries", default = "$default", owner = "platform", why = "why", range = "$range")
            val retries: Int
        }
        """.trimIndent()
}
