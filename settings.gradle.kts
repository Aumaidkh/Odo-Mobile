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
include(":core:platform")
include(":core:sync")
include(":core:triptracker")

include(":feature:auth")
include(":feature:auto-odometer")
include(":feature:billscanner")
include(":feature:healthscore")
include(":feature:onboarding")
include(":feature:paywall")
include(":feature:reminders")
include(":feature:servicelog")
include(":feature:cost-tracker")
include(":feature:document-vault")
include(":feature:dashboard")
include(":feature:fairness-check")
include(":feature:garage")
include(":feature:profile")
include(":feature:refuel")
include(":feature:support")
include(":feature:timeline")
include(":feature:challan")

include(":observability:logging")
include(":observability:analytics")
include(":observability:performance")
include(":observability:crashreporting")

include(":infrastructure:ai")
include(":infrastructure:billing")
include(":infrastructure:database")
include(":infrastructure:firebase:analytics")
include(":infrastructure:firebase:auth")
include(":infrastructure:firebase:crashlytics")
include(":infrastructure:firebase:performance")
include(":infrastructure:firebase:remoteconfig")
include(":infrastructure:supabase")
