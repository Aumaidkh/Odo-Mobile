package com.hopcape.odo.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These assert what has to hold for *any* flavor, not what a particular one produces —
 * the flavor a test run compiles as is decided by the Gradle task name
 * (`core/common/build.gradle.kts`), so pinning "release" here would make the suite fail
 * the first time someone ran it another way.
 *
 * What they are really guarding is the split between base value and suffix. Those come
 * from four separate BuildKonfig fields, and a build type that sets only one of a pair
 * would otherwise ship an app reporting an identity it does not have.
 */
class BuildInfoTest {

    @Test
    fun applicationId_isTheBaseIdPlusThisBuildTypesSuffix() {
        assertEquals(BuildInfo.baseApplicationId + BuildInfo.applicationIdSuffix, BuildInfo.applicationId)
        assertTrue(
            BuildInfo.applicationId.startsWith(BuildInfo.baseApplicationId),
            "a suffix must extend the base ID, never replace it: ${BuildInfo.applicationId}",
        )
    }

    @Test
    fun versionName_isTheBaseVersionPlusThisBuildTypesSuffix() {
        assertEquals(BuildInfo.baseVersionName + BuildInfo.versionNameSuffix, BuildInfo.versionName)
        assertTrue(BuildInfo.baseVersionName.isNotBlank(), "the version catalog must supply a version name")
    }

    @Test
    fun displayVersion_carriesBothTheVersionAndTheBuildNumber() {
        assertEquals("${BuildInfo.versionName} (${BuildInfo.versionCode})", BuildInfo.displayVersion)
    }

    @Test
    fun exactlyOneVariantShorthandIsTrue() {
        val shorthands = listOf(BuildInfo.isDebug, BuildInfo.isStage, BuildInfo.isRelease)
        assertEquals(1, shorthands.count { it }, "variant ${BuildInfo.variant} matched ${shorthands.count { it }} shorthands")
    }

    @Test
    fun onlyReleaseShipsWithoutSuffixes() {
        // A release build is the one that installs under the plain application ID and the
        // plain version. Anything else has to be distinguishable from it on a device.
        if (BuildInfo.isRelease) {
            assertEquals("", BuildInfo.applicationIdSuffix)
            assertEquals("", BuildInfo.versionNameSuffix)
        } else {
            assertTrue(BuildInfo.applicationIdSuffix.isNotEmpty(), "${BuildInfo.variant} needs its own application ID")
            assertTrue(BuildInfo.versionNameSuffix.isNotEmpty(), "${BuildInfo.variant} needs its own version name")
        }
    }
}
