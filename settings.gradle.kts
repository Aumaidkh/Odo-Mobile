rootProject.name = "Odo"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Wire in the build-logic composite build so the odo.* convention plugins
    // are resolvable from every module's `plugins { }` block.
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:navigation")

include(":feature:onboarding")
include(":feature:servicelog")

include(":observability:logging")
include(":observability:analytics")
include(":observability:performance")
include(":observability:crashreporting")