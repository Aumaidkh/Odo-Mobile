package com.hopcape.logging.internal.utils

import com.hopcape.logging.api.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SafeMapTest {

    @Test
    fun get_onMissingKey_isNull() {
        val map = SafeMap<String, LogLevel>()
        assertNull(map["absent"])
    }

    @Test
    fun set_thenGet_roundTrips() {
        val map = SafeMap<String, LogLevel>()
        map["Sync"] = LogLevel.VERBOSE
        assertEquals(LogLevel.VERBOSE, map["Sync"])
    }

    @Test
    fun set_overwritesExistingValue() {
        val map = SafeMap<String, LogLevel>()
        map["Sync"] = LogLevel.VERBOSE
        map["Sync"] = LogLevel.ERROR
        assertEquals(LogLevel.ERROR, map["Sync"])
    }

    @Test
    fun remove_deletesKey() {
        val map = SafeMap<String, LogLevel>()
        map["Sync"] = LogLevel.VERBOSE
        map.remove("Sync")
        assertNull(map["Sync"])
    }

    @Test
    fun keys_areIndependent() {
        val map = SafeMap<String, LogLevel>()
        map["a"] = LogLevel.INFO
        map["b"] = LogLevel.WARN
        map.remove("a")
        assertNull(map["a"])
        assertEquals(LogLevel.WARN, map["b"])
    }
}
