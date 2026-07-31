package com.hopcape.odo.feature.documentvault

import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import com.hopcape.odo.feature.documentvault.platform.IosDocumentFileStore
import org.koin.dsl.module

/**
 * The document vault's iOS-only bindings, registered by `MainViewController` alongside
 * [documentVaultModule]. The mirror of `documentVaultAndroidModule`.
 *
 * The store it binds refuses every write (Phase 2 builds the real one), which is still
 * better than leaving the port unbound: a missing definition fails as a Koin crash on a
 * screen that has nothing to do with storage.
 */
val documentVaultIosModule = module {
    single<DocumentFileStore> { IosDocumentFileStore() }
}
