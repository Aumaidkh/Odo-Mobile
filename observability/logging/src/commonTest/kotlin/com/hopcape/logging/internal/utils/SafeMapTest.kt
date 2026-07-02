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
        map["BLE"] = LogLevel.VERBOSE
        assertEquals(LogLevel.VERBOSE, map["BLE"])
    }

    @Test
    fun set_overwritesExistingValue() {
        val map = SafeMap<String, LogLevel>()
        map["BLE"] = LogLevel.VERBOSE
        map["BLE"] = LogLevel.ERROR
        assertEquals(LogLevel.ERROR, map["BLE"])
    }

    @Test
    fun remove_deletesKey() {
        val map = SafeMap<String, LogLevel>()
        map["BLE"] = LogLevel.VERBOSE
        map.remove("BLE")
        assertNull(map["BLE"])
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
