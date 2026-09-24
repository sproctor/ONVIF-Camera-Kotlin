package com.seanproctor.onvifcamera.encoders

import com.seanproctor.onvifcamera.MediaProfile
import com.seanproctor.onvifcamera.MediaService
import com.seanproctor.onvifcamera.OnvifCommands
import com.seanproctor.onvifcamera.WsSecurity
import kotlin.time.Instant
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private const val DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl"
private const val MEDIA10_NS = "http://www.onvif.org/ver10/media/wsdl"
private const val MEDIA20_NS = "http://www.onvif.org/ver20/media/wsdl"
private const val WSA_NS = "http://schemas.xmlsoap.org/ws/2004/08/addressing"
private const val WSD_NS = "http://schemas.xmlsoap.org/ws/2005/04/discovery"
private const val NETWORK_NS = "http://www.onvif.org/ver10/network/wsdl"
private const val WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
private const val WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"

/**
 * The commands are produced by kotlinx serialization, so the SOAP prefix and whitespace are an
 * implementation detail of the serializer. These tests therefore assert on element local names,
 * namespaces and text content rather than on the exact byte output.
 */
class OnvifCommandsTest {

    private val profile = MediaProfile(token = "Profile_1", name = "MainStream", encoding = "H264")

    /** Drains the reader to assert the document is well-formed XML (throws on malformed input). */
    private fun assertWellFormed(xml: String) {
        val reader = xmlStreaming.newReader(xml)
        while (reader.hasNext()) {
            reader.next()
        }
    }

    private fun hasElement(xml: String, localName: String): Boolean {
        val reader = xmlStreaming.newReader(xml)
        while (reader.hasNext()) {
            if (reader.next() == EventType.START_ELEMENT && reader.localName == localName) return true
        }
        return false
    }

    /** Returns the namespace URI of the first element with the given local name. */
    private fun namespaceOf(xml: String, localName: String): String {
        val reader = xmlStreaming.newReader(xml)
        while (reader.hasNext()) {
            if (reader.next() == EventType.START_ELEMENT && reader.localName == localName) {
                return reader.namespaceURI
            }
        }
        fail("Element <$localName> not found in: $xml")
    }

    /** Returns the decoded text content of the first element with the given local name. */
    private fun readElementText(xml: String, localName: String): String {
        val reader = xmlStreaming.newReader(xml)
        while (reader.hasNext()) {
            if (reader.next() == EventType.START_ELEMENT && reader.localName == localName) {
                val text = StringBuilder()
                while (reader.next() != EventType.END_ELEMENT) {
                    when (reader.eventType) {
                        EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> text.append(reader.text)
                        else -> {}
                    }
                }
                return text.toString()
            }
        }
        fail("Element <$localName> not found in: $xml")
    }

    @Test
    fun testStreamUriCommandUsesProfileTokenAndDefaultProtocol() {
        val command = OnvifCommands.getStreamURICommand(MediaService.MEDIA2, profile)
        assertWellFormed(command)
        assertEquals(MEDIA20_NS, namespaceOf(command, "GetStreamUri"))
        assertEquals("Profile_1", readElementText(command, "ProfileToken"))
        assertEquals("RTSP", readElementText(command, "Protocol"))
    }

    @Test
    fun testMedia1StreamUriCommandSpellsOutTheTransport() {
        val command = OnvifCommands.getStreamURICommand(MediaService.MEDIA1, profile)
        assertWellFormed(command)
        assertEquals(MEDIA10_NS, namespaceOf(command, "GetStreamUri"))
        assertEquals(MEDIA10_NS, namespaceOf(command, "StreamSetup"))
        assertEquals("RTP-Unicast", readElementText(command, "Stream"))
        assertEquals("RTSP", readElementText(command, "Protocol"))
        assertEquals("Profile_1", readElementText(command, "ProfileToken"))
        // The schema orders StreamSetup before ProfileToken; gSOAP devices reject the reverse.
        assertTrue(command.indexOf("StreamSetup") < command.indexOf("ProfileToken"), command)
    }

    @Test
    fun testMedia1ProfilesAndSnapshotCommands() {
        val profiles = OnvifCommands.profilesCommand(MediaService.MEDIA1)
        assertWellFormed(profiles)
        assertEquals(MEDIA10_NS, namespaceOf(profiles, "GetProfiles"))
        assertFalse(hasElement(profiles, "Type"), "Media1 GetProfiles takes no Type: $profiles")

        val snapshot = OnvifCommands.getSnapshotURICommand(MediaService.MEDIA1, profile)
        assertWellFormed(snapshot)
        assertEquals(MEDIA10_NS, namespaceOf(snapshot, "GetSnapshotUri"))
        assertEquals("Profile_1", readElementText(snapshot, "ProfileToken"))
    }

    @Test
    fun testStreamUriCommandUsesSuppliedProtocol() {
        val command = OnvifCommands.getStreamURICommand(MediaService.MEDIA2, profile, protocol = "HTTP")
        assertWellFormed(command)
        assertEquals("HTTP", readElementText(command, "Protocol"))
    }

    @Test
    fun testSnapshotUriCommandUsesProfileTokenWithoutProtocol() {
        val command = OnvifCommands.getSnapshotURICommand(MediaService.MEDIA2, profile)
        assertWellFormed(command)
        assertEquals(MEDIA20_NS, namespaceOf(command, "GetSnapshotUri"))
        assertEquals("Profile_1", readElementText(command, "ProfileToken"))
        assertFalse(hasElement(command, "Protocol"))
    }

    @Test
    fun testProfilesCommand() {
        val command = OnvifCommands.profilesCommand(MediaService.MEDIA2)
        assertWellFormed(command)
        assertEquals(MEDIA20_NS, namespaceOf(command, "GetProfiles"))
        // Media2 returns tokens and names only unless asked for configurations; the video
        // encoder is the one MediaProfile is built from.
        assertEquals(MEDIA20_NS, namespaceOf(command, "Type"))
        assertEquals("VideoEncoder", readElementText(command, "Type"))
    }

    @Test
    fun testDeviceInformationCommand() {
        val command = OnvifCommands.deviceInformationCommand()
        assertWellFormed(command)
        assertEquals(DEVICE_NS, namespaceOf(command, "GetDeviceInformation"))
        assertFalse(hasElement(command, "Header"), "no credentials, so no SOAP header: $command")
    }

    @Test
    fun testCredentialsGoInAWsSecurityUsernameTokenWithAPasswordDigest() {
        val security = WsSecurity.usernameToken("admin", "secret", Instant.parse("2026-09-22T12:00:00Z"))
        val command = OnvifCommands.deviceInformationCommand(security)
        assertWellFormed(command)
        assertEquals(WSSE_NS, namespaceOf(command, "Security"))
        assertEquals(WSSE_NS, namespaceOf(command, "UsernameToken"))
        assertEquals("admin", readElementText(command, "Username"))
        assertEquals(WSU_NS, namespaceOf(command, "Created"))
        assertEquals("2026-09-22T12:00:00Z", readElementText(command, "Created"))
        assertContains(command, "#PasswordDigest\"", message = "the Password must be typed as a digest")
        assertFalse(command.contains("secret"), "the password itself must never appear: $command")
        // The header precedes the body, as SOAP requires.
        assertTrue(command.indexOf("Security") < command.indexOf("GetDeviceInformation"), command)
    }

    @Test
    fun testCreatedIsTruncatedToWholeSeconds() {
        val security = WsSecurity.usernameToken("admin", "secret", Instant.parse("2026-09-22T12:00:00.750Z"))
        val command = OnvifCommands.deviceInformationCommand(security)
        assertEquals("2026-09-22T12:00:00Z", readElementText(command, "Created"))
    }

    @Test
    fun testServicesCommandExcludesCapabilities() {
        val command = OnvifCommands.servicesCommand()
        assertWellFormed(command)
        assertEquals(DEVICE_NS, namespaceOf(command, "GetServices"))
        assertEquals("false", readElementText(command, "IncludeCapability"))
    }

    @Test
    fun testHostnameCommand() {
        val command = OnvifCommands.getHostnameCommand
        assertWellFormed(command)
        assertEquals(DEVICE_NS, namespaceOf(command, "GetHostname"))
    }

    @Test
    fun testSystemDateAndTimeCommand() {
        val command = OnvifCommands.getSystemDateAndTimeCommand
        assertWellFormed(command)
        assertEquals(DEVICE_NS, namespaceOf(command, "GetSystemDateAndTime"))
    }

    @Test
    fun testStreamUriCommandEscapesSpecialCharactersInToken() {
        // A token containing XML-significant characters must not break the SOAP body; it should
        // round-trip back to its original value once the document is parsed.
        val nastyToken = "tok&<>\"'en"
        val command = OnvifCommands.getStreamURICommand(
            MediaService.MEDIA2,
            MediaProfile(token = nastyToken, name = null, encoding = "H264"),
        )
        assertWellFormed(command)
        assertFalse(command.contains("<>"), "Raw markup leaked into the request: $command")
        assertEquals(nastyToken, readElementText(command, "ProfileToken"))
    }

    @Test
    fun testProbeCommandEmbedsMessageId() {
        val command = OnvifCommands.probeCommand("abc-123")
        assertWellFormed(command)
        assertEquals("uuid:abc-123", readElementText(command, "MessageID"))
        assertEquals(WSA_NS, namespaceOf(command, "MessageID"))
        assertEquals(
            "http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe",
            readElementText(command, "Action"),
        )
        assertContains(command, "mustUnderstand=\"1\"")
    }

    @Test
    fun testProbeCommandTargetsNetworkVideoTransmitter() {
        val command = OnvifCommands.probeCommand("abc-123")
        assertWellFormed(command)
        assertEquals(WSD_NS, namespaceOf(command, "Probe"))
        assertEquals(WSD_NS, namespaceOf(command, "Types"))
        // The Types content is a QName that must resolve to the ONVIF network namespace.
        assertTrue(readElementText(command, "Types").endsWith(":NetworkVideoTransmitter"))
        assertContains(command, NETWORK_NS)
    }

    @Test
    fun testProbeCommandVariesByMessageId() {
        assertEquals(
            OnvifCommands.probeCommand("same"),
            OnvifCommands.probeCommand("same"),
        )
        assertTrue(OnvifCommands.probeCommand("one") != OnvifCommands.probeCommand("two"))
    }
}
