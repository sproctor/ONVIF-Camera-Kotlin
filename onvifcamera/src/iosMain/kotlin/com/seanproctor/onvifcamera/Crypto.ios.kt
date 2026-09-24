package com.seanproctor.onvifcamera

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA1_CTX
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1_Final
import platform.CoreCrypto.CC_SHA1_Init
import platform.CoreCrypto.CC_SHA1_Update
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

// CommonCrypto marks SHA-1 deprecated as a hash for new designs; the WS-Security password
// digest is defined as SHA-1, so there is no choice here.
@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
internal actual fun sha1(vararg parts: ByteArray): ByteArray = memScoped {
    val context = alloc<CC_SHA1_CTX>()
    CC_SHA1_Init(context.ptr)
    for (part in parts) {
        if (part.isEmpty()) continue
        part.usePinned { CC_SHA1_Update(context.ptr, it.addressOf(0), part.size.convert()) }
    }
    val digest = UByteArray(CC_SHA1_DIGEST_LENGTH)
    digest.usePinned { CC_SHA1_Final(it.addressOf(0), context.ptr) }
    digest.asByteArray()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
    check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
    return bytes
}
