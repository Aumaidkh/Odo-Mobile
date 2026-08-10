plugins {
    // Firebase Phone Auth adapter for :core:domain's PhoneVerifier port. Depends
    // inward on :core:domain (the port) and :core:common (runCatchingCancellable)
    // only. It knows nothing about Supabase — the gateway that trades a verified
    // number for a session lives in :infrastructure:supabase and reaches this
    // through the port, so neither module imports the other.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.firebase.auth"
    }

    sourceSets {
        commonMain.dependencies {
            // PhoneVerifier, VerifiedPhoneToken, PhoneNumber, DomainError.
            implementation(projects.core.domain)
            // runCatchingCancellableSuspend — rethrows CancellationException instead of
            // swallowing it, unlike stdlib's runCatching. Every Firebase gateway in this
            // repo reports an SDK failure rather than throwing it.
            implementation(projects.core.common)
            // onDiagnostic is wired to the logger in firebaseAuthModule, same contract as
            // :infrastructure:firebase:remoteconfig.
            implementation(projects.observability.logging)
            // Arrow's Either is the port's return type.
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            // Task.await() — bridges signInWithCredential's Task<AuthResult> and
            // getIdToken's Task<GetTokenResult> to suspend calls.
            implementation(libs.kotlinx.coroutines.play.services)
        }
    }
}

dependencies {
    // Native Android SDK, not gitlive — gitlive's firebase-auth does not expose
    // PhoneAuthProvider in commonMain, and phone verification needs the Activity-bound
    // Android API anyway. Version comes from the BOM, same as libs.firebase.perf.
    "androidMainImplementation"(platform(libs.firebase.bom))
    "androidMainImplementation"(libs.firebase.auth)
}
