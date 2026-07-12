package com.hopcape.odo.feature.documentvault.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** Android actual — the system document picker, filtered to PDFs and images. */
@Composable
actual fun rememberFilePicker(onPicked: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPicked(uri?.toString())
    }
    return { launcher.launch(arrayOf("application/pdf", "image/*")) }
}
