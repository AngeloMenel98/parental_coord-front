package com.parental.shared.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
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
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Secretos en el Keychain de iOS como generic password. La accesibilidad
 * `ThisDeviceOnly` evita que el valor viaje en backups o migre a otro dispositivo.
 */
@OptIn(ExperimentalForeignApi::class)
internal object KeychainStorage {
    private const val SERVICE = "com.parental.coordination"
    private val log = Logger.withTag("KeychainStorage")

    /** Devuelve true si el valor quedó guardado. */
    @Suppress("CAST_NEVER_SUCCEEDS")
    fun save(key: String, value: String): Boolean {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        remove(key)

        val cfData = CFBridgingRetain(data)
        val status = try {
            withQuery(
                key,
                kSecValueData to cfData,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ) { query -> SecItemAdd(query, null) }
        } finally {
            CFRelease(cfData)
        }
        if (status != errSecSuccess) log.e { "SecItemAdd failed: $status" }
        return status == errSecSuccess
    }

    @OptIn(BetaInteropApi::class)
    fun load(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(
            key,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        ) { query -> SecItemCopyMatching(query, result.ptr) }
        if (status != errSecSuccess) return@memScoped null

        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    fun remove(key: String) {
        withQuery(key) { query -> SecItemDelete(query) }
    }

    private inline fun <T> withQuery(
        key: String,
        vararg attributes: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(key)
        val query = CFDictionaryCreateMutable(
            null,
            (attributes.size + 3).convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            attributes.forEach { (name, value) -> CFDictionaryAddValue(query, name, value) }
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(account)
        }
    }
}
