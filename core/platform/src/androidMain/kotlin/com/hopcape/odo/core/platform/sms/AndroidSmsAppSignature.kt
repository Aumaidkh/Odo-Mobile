package com.hopcape.odo.core.platform.sms

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Computes the hash Google's SMS Retriever expects, exactly the way Google specifies it.
 *
 * The recipe is not negotiable and not obvious: SHA-256 over `"$packageName $signature"`, then
 * the first nine bytes of the digest, base64-encoded, then the first eleven characters. Any
 * deviation produces a hash that looks plausible and silently never matches.
 *
 * Written out rather than pulled from a library because the whole point is to be able to read
 * the value off a running build and paste it into the SMS template.
 */
internal class AndroidSmsAppSignature(private val context: Context) : SmsAppSignature {

    override suspend fun current(): List<String> = withContext(Dispatchers.Default) {
        runCatching { signatures().map { it.toRetrieverHash() } }.getOrElse { emptyList() }
    }

    /** Every certificate this build is signed by. */
    private fun signatures(): List<ByteArray> {
        val packageManager = context.packageManager
        val name = context.packageName

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return emptyList()
            // Rotated keys mean more than one certificate can be valid.
            val certificates = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            certificates.orEmpty().map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(name, PackageManager.GET_SIGNATURES)
                .signatures.orEmpty().map { it.toByteArray() }
        }
    }

    private fun ByteArray.toRetrieverHash(): String {
        val payload = "${context.packageName} ${Base64.encodeToString(this, Base64.NO_WRAP)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.encodeToByteArray())
        return Base64.encodeToString(digest.copyOf(DIGEST_BYTES), Base64.NO_WRAP or Base64.NO_PADDING)
            .take(HASH_LENGTH)
    }

    private companion object {
        /** Google's spec: the first nine bytes of the digest, and eleven characters of base64. */
        const val DIGEST_BYTES = 9
        const val HASH_LENGTH = 11
    }
}
