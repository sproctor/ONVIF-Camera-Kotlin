package com.seanproctor.onvifcamera

import kotlin.test.Test
import kotlin.test.assertEquals

class ChallengeSchemesTest {
    @Test
    fun oneSchemePerHeader() {
        assertEquals(listOf("digest"), challengeSchemes("""Digest realm="cam", qop="auth", nonce="abc", opaque="""""))
        assertEquals(listOf("basic"), challengeSchemes("""Basic realm="cam""""))
    }

    @Test
    fun severalChallengesInOneHeader() {
        assertEquals(
            listOf("basic", "digest"),
            challengeSchemes("""Basic realm="cam", Digest realm="cam", nonce="abc", algorithm=MD5"""),
        )
    }

    @Test
    fun quotedCommasAndSchemeNamesDoNotCount() {
        assertEquals(listOf("basic"), challengeSchemes("""Basic realm="Login, Digest area""""))
    }

    @Test
    fun aBareSchemeCounts() {
        assertEquals(listOf("negotiate"), challengeSchemes("Negotiate"))
    }
}
