package com.hopcape.odo.core.platform.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM under a hardware-backed Keystore key, with the ciphertext in ordinary
 * `SharedPreferences`.
 *
 * The key itself never leaves the Keystore — this code can ask it to encrypt and decrypt but
 * cannot read it, which is the whole point: an attacker with the preferences file has bytes
 * they cannot turn back into a token without the device.
 *
 * Deliberately hand-rolled rather than `EncryptedSharedPreferences`. That library is
 * deprecated, and what it adds over this — encrypted *keys* as well as values — buys nothing
 * here, because the key names are already public knowledge in this file.
 *
 * GCM, not CBC: it authenticates as well as encrypts, so a tampered value fails to decrypt
 * instead of decrypting to garbage that then gets sent as a bearer token. A fresh IV is
 * generated per write by the cipher and stored alongside the ciphertext — reusing one under
 * the same key is the classic way to break GCM.
 */
internal class AndroidSecureStore(context: Context) : SecureStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val ciphertext = cipher.doFinal(value.encodeToByteArray())
            // IV first, then ciphertext, as one blob. Keeping them together means there is no
            // way to end up with one and not the other.
            val blob = cipher.iv + ciphertext
            prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
        }
        Unit
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(key, null) ?: return@withContext null
        runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            }
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES).decodeToString()
        }.getOrNull()
        // Null on failure rather than a throw. A value that will not decrypt is a value the
        // Keystore key no longer matches — the user cleared credentials, restored a backup
        // onto another device, or changed their lock screen. The honest answer is "no
        // session", which sends them to sign in again.
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key).commit() }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
    }

    /**
     * The Keystore key, created on first use.
     *
     * Not tied to user authentication (`setUserAuthenticationRequired`): Odo has to sync in
     * the background, when nobody is holding the phone to unlock it.
     */
    private fun secretKey(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PREFS = "odo_secure"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "odo_secure_store"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
