# R8 rules for the release build. Full mode (AGP's default) plus resource shrinking.
#
# Most of the stack ships its own consumer rules inside its artifacts — AndroidX, Compose,
# Firebase, CameraX, ML Kit, WorkManager, OkHttp and Okio all do — so only what those do
# not cover belongs here. Everything below either states why a class survives obfuscation
# or suppresses a warning about a dependency that is genuinely absent at runtime.
#
# Stage is not minified, so these rules are exercised for the first time on the release
# build itself. Smoke-test a release APK on a device before uploading it.

# ---------------------------------------------------------------------------
# Crash reports
# ---------------------------------------------------------------------------
# Keep line numbers so a stack trace has them, and hide the original file name so the
# class name is still obfuscated. Crashlytics uploads the mapping file automatically for
# minified builds, which is what turns these back into readable traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# The serialization compiler plugin resolves most call sites to a direct
# `Foo.serializer()` at compile time, but a lookup that falls back to reflection finds the
# serializer through the companion object or the `INSTANCE` field. These are the rules
# from the kotlinx.serialization README, unchanged.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep the `Companion` object field of a serializable class, so the lookup does not have
# to go through `getDeclaredClasses`.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on the companion object, named or not.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` for serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Navigation 3
# ---------------------------------------------------------------------------
# Route keys are `@Serializable` NavKey implementations, saved and restored across process
# death by type. The serialization rules above cover the serializer; this keeps the key
# types themselves, which are otherwise only ever referenced through a `NavKey` supertype.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

# ---------------------------------------------------------------------------
# Ktor
# ---------------------------------------------------------------------------
# The engine is passed to `HttpClient(engine)` explicitly (OkHttp on Android), so none of
# Ktor's ServiceLoader discovery runs and nothing needs keeping for it. What is left is
# optional integrations Ktor compiles against but the app does not ship.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**
# Ktor's internal state machines use volatile fields updated through atomic field updaters,
# which resolve them by name.
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# ---------------------------------------------------------------------------
# Koin
# ---------------------------------------------------------------------------
# Definitions are plain lambdas, not reflection, and Koin keys every definition by the
# class's runtime name — obfuscation renames the definition side and the lookup side
# together, so the keys still match. Nothing to keep; the module only needs the warnings
# about Koin's optional Android/Java integrations silenced.
-dontwarn org.koin.**

# ---------------------------------------------------------------------------
# Room (WorkManager's own database)
# ---------------------------------------------------------------------------
# Odo does not use Room directly — SQLDelight is the app's database — but WorkManager
# stores its queue in one, and Room builds its generated `*_Impl` class by name through a
# no-argument constructor. R8 in full mode drops that constructor because nothing calls it
# in bytecode, and the app then dies during androidx.startup with
# "NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []" before any of
# our code runs. Caught by smoke-testing the release APK on a device.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# ---------------------------------------------------------------------------
# WorkManager
# ---------------------------------------------------------------------------
# WorkManager instantiates a worker from the class name recorded in its database, through
# the (Context, WorkerParameters) constructor. androidx.work ships this rule itself; it is
# repeated because a worker surviving R8 is the difference between reminders firing and
# silently never running, and that failure only shows up days after a release.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------------------
# SQLDelight
# ---------------------------------------------------------------------------
# Generated code with no reflection, so nothing to keep. The native driver links against
# the JDBC one's classes, which an Android build does not have.
-dontwarn org.sqlite.**
-dontwarn app.cash.sqldelight.driver.jdbc.**

# ---------------------------------------------------------------------------
# Kotlin / coroutines
# ---------------------------------------------------------------------------
# `kotlin.Metadata` is what any reflective serializer lookup reads to find a companion.
-keepattributes *Annotation*
-dontwarn kotlin.reflect.jvm.internal.**
# Coroutines' internal service loader files are stripped by the consumer rules the
# artifact ships; these are the classes those rules leave dangling references to.
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
