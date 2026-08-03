package com.hopcape.odo.core.platform.secure

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The iOS Keychain, one generic-password item per key.
 *
 * `kSecAttrAccessibleAfterFirstUnlock` rather than `WhenUnlocked`: Odo syncs in the
 * background, and a token the app cannot read while the screen is locked would mean sync
 * only ever works with the phone in someone's hand. "After first unlock" still keeps the
 * items encrypted until the device has been unlocked once since boot.
 *
 * **Keychain items outlive an app uninstall on iOS.** That is Apple's behaviour, not a bug
 * here, but it means a reinstall can find a stale session belonging to the previous install.
 * Whoever restores a session has to treat a token that fails to refresh as no session at
 * all, which is what the auth layer does.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosSecureStore : SecureStore {

    override suspend fun put(key: String, value: String) {
        // Delete first. SecItemAdd fails with errSecDuplicateItem rather than replacing, and
        // an update path would be a second code path doing the same job.
        SecItemDelete(baseQuery(key))

        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to key,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
        )
        SecItemAdd(query.toCFDictionary(), null)
    }

    override suspend fun get(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to key,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )

        if (SecItemCopyMatching(query.toCFDictionary(), result.ptr) != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data, NSUTF8StringEncoding) as String?
    }

    override suspend fun remove(key: String) {
        SecItemDelete(baseQuery(key))
    }

    override suspend fun clear() {
        // Everything this service owns, in one call — no need to know the key names.
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
        )
        SecItemDelete(query.toCFDictionary())
    }

    /** Identifies one item: this app's service, plus the key as the account name. */
    private fun baseQuery(key: String): CFDictionaryRef? = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to SERVICE,
        kSecAttrAccount to key,
    ).toCFDictionary()

    /**
     * A Kotlin map as the `CFDictionary` the Security framework wants.
     *
     * `CFBridgingRetain` hands ownership to Core Foundation. These dictionaries are consumed
     * by the `SecItem*` call they are passed to and are small and short-lived, so the
     * retain is not balanced here — matching how the same bridge is used across the
     * Kotlin/Native ecosystem.
     */
    private fun Map<Any?, Any?>.toCFDictionary(): CFDictionaryRef? =
        CFBridgingRetain(this) as CFDictionaryRef?

    private companion object {
        const val SERVICE = "com.hopcape.odo"
    }
}
