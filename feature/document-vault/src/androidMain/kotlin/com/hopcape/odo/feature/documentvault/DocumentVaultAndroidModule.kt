package com.hopcape.odo.feature.documentvault

import android.content.Context
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import com.hopcape.odo.feature.documentvault.platform.AndroidDocumentFileStore
import org.koin.dsl.module

/**
 * The document vault's Android-only bindings, registered by the `:app` bootstrap alongside
 * [documentVaultModule].
 *
 * Separate from the shared module because storing a file needs a `Context`, and that is the
 * one thing common code cannot ask for. Koin already holds it — `androidContext()` in the
 * bootstrap declares it — so this resolves the Context rather than taking it as a parameter,
 * and the app's only job is to list the module.
 */
val documentVaultAndroidModule = module {
    single<DocumentFileStore> { AndroidDocumentFileStore(context = get<Context>()) }
}
