package com.seanproctor.onvifcamera

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CryptoTest {
    @Test
    fun sha1MatchesTheFips180Vector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc".encodeToByteArray()).toHexString())
    }

    @Test
    fun sha1HashesThePartsConcatenated() {
        assertContentEquals(sha1("abc".encodeToByteArray()), sha1("a".encodeToByteArray(), "bc".encodeToByteArray()))
    }

    @Test
    fun secureRandomBytesHaveTheSizeAskedForAndDiffer() {
        val first = secureRandomBytes(16)
        assertEquals(16, first.size)
        assertFalse(first.contentEquals(secureRandomBytes(16)))
    }
}
