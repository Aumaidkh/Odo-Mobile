package com.hopcape.odo.core.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigKeyTest {

    private fun key(name: String = "sample_key", owner: String = "platform", why: String = "why") =
        ConfigKey(key = name, type = ConfigType.STRING, default = "", owner = owner, why = why)

    @Test
    fun `a well-formed key is accepted`() {
        key("legal_privacy_policy_url")
        key("a")
        key("a1_b2")
    }

    @Test
    fun `key format is enforced`() {
        // The processor checks this at build time. It is checked again here because a
        // group can also be written by hand, as the sample one in this module is.
        listOf("Sample_Key", "1_leading_digit", "trailing-dash", "has space", "", "_leading") .forEach {
            assertFailsWith<IllegalArgumentException>("expected '$it' to be rejected") { key(it) }
        }
    }

    @Test
    fun `owner and why are required`() {
        assertTrue(assertFailsWith<IllegalArgumentException> { key(owner = " ") }.message!!.contains("owner"))
        assertTrue(assertFailsWith<IllegalArgumentException> { key(why = " ") }.message!!.contains("why"))
    }
}
