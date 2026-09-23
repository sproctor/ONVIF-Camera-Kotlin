package com.seanproctor.onvifcamera.conformance

import com.seanproctor.onvifcamera.MediaService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal const val SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope"
internal const val DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl"
internal const val MEDIA1_NS = "http://www.onvif.org/ver10/media/wsdl"
internal const val MEDIA2_NS = "http://www.onvif.org/ver20/media/wsdl"
private const val SCHEMA_NS = "http://www.onvif.org/ver10/schema"

/** tr2:ConfigurationEnumeration, the values Media2 GetProfiles accepts in Type. */
private val MEDIA2_CONFIGURATION_TYPES = setOf(
    "All", "VideoSource", "VideoEncoder", "AudioSource", "AudioEncoder", "AudioOutput",
    "AudioDecoder", "Metadata", "Analytics", "PTZ", "Receiver",
)

/** tr2:TransportProtocol, the values Media2 GetStreamUri accepts in Protocol. */
private val MEDIA2_TRANSPORTS = setOf(
    "RtspUnicast", "RtspMulticast", "RtspsUnicast", "RtspsMulticast", "RTSP", "RtspOverHttp",
)

internal const val MAIN_PROFILE = "Profile000"
internal const val SUB_PROFILE = "Profile001"

// WS-Security, spelled out here rather than imported so the check stays independent of the code
// it checks.
private const val WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
private const val WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
private const val PASSWORD_DIGEST_TYPE =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"

internal const val SNAPSHOT_PATH = "/onvifsnapshot/media_service/snapshot"

/** Not a decodable picture, but it starts like one (SOI, APP0) and that is what the checks need. */
internal val SNAPSHOT_JPEG: ByteArray =
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
        "conformance snapshot".encodeToByteArray() +
        byteArrayOf(0xFF.toByte(), 0xD9.toByte())

/** Operations the ONVIF access policy opens to unauthenticated callers. */
private val PRE_AUTH_OPERATIONS = setOf("GetSystemDateAndTime", "GetServices", "GetServiceCapabilities", "GetHostname")

/**
 * A minimal ONVIF device on loopback, built from the JDK's HTTP server so the suite needs no
 * dependency. It answers the operations this library uses with spec-shaped responses and, more
 * to the point, checks every request against the specification as it arrives: SOAP 1.2, the
 * content type, Digest authentication, the namespace each operation lives in, required elements,
 * element order where the schema fixes it, and enumerated values. Anything wrong is recorded in
 * [violations]; the tests assert that list is empty, so one run reports every problem at once.
 *
 * Its profiles mirror a Lorex LNB45ABB: an H.265 2688x1520 main stream and an H.264 704x480 sub
 * stream. Every URI it advertises names [advertisedHost], which is deliberately not the address
 * the client connected to, so the client's host rewriting is exercised.
 */
internal class FakeOnvifDevice(
    private val media: Set<MediaService> = setOf(MediaService.MEDIA2, MediaService.MEDIA1),
    private val credentials: Pair<String, String>? = "admin" to "secret",
    /**
     * The schemes a 401 offers, one WWW-Authenticate header each, in this order: `Digest`, as
     * ONVIF requires, `Basic` for the rare device that uses only that, or both, as some firmware
     * sends. Answering Basic when Digest was offered is a violation, whatever the order.
     */
    private val challengeSchemes: List<String> = listOf("Digest"),
    /**
     * Answer a Basic attempt with a fresh 401 offering Digest only, as a device might after a
     * firmware update or behind a proxy. A client must not retry with Basic still attached.
     */
    private val digestAfterBasic: Boolean = false,
    /** Authenticate in SOAP only: never challenge at the HTTP layer, answer NotAuthorized faults instead. */
    private val wsSecurityOnly: Boolean = false,
    /** Ignore any UsernameToken and authenticate at the HTTP layer only, as Axis firmware does. */
    private val ignoreUsernameToken: Boolean = false,
    /** The device clock minus this host's. Token timestamps must follow the device clock. */
    private val clockSkew: Duration = Duration.ZERO,
    /** HTTP status a SOAP fault is sent with; the spec wants 400/500, some cameras send 200. */
    private val faultStatus: Int = 400,
    /** Answer every media operation with a NotAuthorized fault (HTTP 200), as some cameras do. */
    private val notAuthorizedAsFault: Boolean = false,
    /** Operations answered with HTTP 200 and an HTML page instead of SOAP. */
    private val htmlPageFor: Set<String> = emptySet(),
    /** Operations answered with HTTP 403. */
    private val forbidden: Set<String> = emptySet(),
    private val advertisedHost: String = "192.0.2.99",
) : AutoCloseable {

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val host: String = InetAddress.getLoopbackAddress().hostAddress
    val port: Int get() = server.address.port
    val deviceServiceUrl: String get() = "http://$host:$port/onvif/device_service"

    /** Specification violations seen in requests, in the order they happened. */
    val violations = CopyOnWriteArrayList<String>()

    /** `"<service> <operation>"` for every request that got past authentication, in order. */
    val operations = CopyOnWriteArrayList<String>()

    /** How many 401 challenges were issued: every one is a round trip the client paid. */
    val challenges = AtomicInteger()

    /** How many requests carried a WS-Security UsernameToken, valid or not. */
    val usernameTokens = AtomicInteger()

    /** How many snapshot GETs were served. */
    val snapshotGets = AtomicInteger()

    private val realm = "onvif-conformance"

    /** What the latest 401 offered; starts as [challengeSchemes], see [digestAfterBasic]. */
    @Volatile
    private var offeredSchemes: List<String> = challengeSchemes
    private val nonce = UUID.randomUUID().toString().replace("-", "")

    init {
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (t: Throwable) {
                violations += "fake device failed handling ${exchange.requestURI}: $t"
                respond(exchange, 500, "text/plain", t.toString())
            }
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    override fun close() = server.stop(0)

    private fun violate(message: String) {
        violations += message
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val body = exchange.requestBody.readBytes().decodeToString()
        if (path == SNAPSHOT_PATH) {
            snapshot(exchange)
            return
        }
        if (exchange.requestMethod != "POST") {
            violate("$path: ${exchange.requestMethod} instead of POST")
            respond(exchange, 405, "text/plain", "")
            return
        }
        val contentType = exchange.requestHeaders.getFirst("Content-Type").orEmpty()
        if (!contentType.startsWith("application/soap+xml")) {
            violate("$path: Content-Type is '$contentType'; SOAP 1.2 over HTTP requires application/soap+xml")
        }
        val request = try {
            parseSoapRequest(body)
        } catch (e: Exception) {
            violate("$path: request body is not XML: ${e.message}")
            respond(exchange, 400, "text/plain", "")
            return
        }
        val envelope = request.envelope
        if (envelope == null || envelope.localName != "Envelope" || envelope.namespace != SOAP12_NS) {
            violate("$path: not a SOAP 1.2 envelope (root is {${envelope?.namespace}}${envelope?.localName})")
        }
        val op = request.operation
        if (op == null) {
            violate("$path: SOAP Body has no operation")
            respondFault(exchange, "Sender", listOf("ActionNotSupported"), "Empty body")
            return
        }
        if (request.first("UsernameToken") != null) usernameTokens.incrementAndGet()
        // Pre-auth operations (ONVIF access policy) are open to everyone; the rest need either a
        // valid UsernameToken in the SOAP header or, unless this device is SOAP-only, HTTP auth.
        if (credentials != null && op.localName !in PRE_AUTH_OPERATIONS) {
            val tokenAccepted = !ignoreUsernameToken && verifyUsernameToken(request, path)
            val accepted = tokenAccepted || (!wsSecurityOnly && authorized(exchange, path))
            if (!accepted) {
                if (wsSecurityOnly) respondFault(exchange, "Sender", listOf("NotAuthorized"), "Sender not Authorized")
                else challenge(exchange)
                return
            }
        }
        val service = path.substringAfterLast('/')
        operations += "$service ${op.localName}"
        when {
            op.localName in forbidden -> respond(exchange, 403, "text/plain", "Forbidden")
            op.localName in htmlPageFor -> respond(exchange, 200, "text/html", "<html><body><form>Please log in</form></body></html>")
            path == "/onvif/device_service" -> device(exchange, op, request)
            path == "/onvif/media2_service" && MediaService.MEDIA2 in media -> media2(exchange, op, request)
            path == "/onvif/media_service" && MediaService.MEDIA1 in media -> media1(exchange, op, request)
            else -> {
                violate("$path: request to a service this device did not advertise")
                respond(exchange, 404, "text/plain", "")
            }
        }
    }

    // ---- Device service -------------------------------------------------------------------

    private fun device(x: HttpExchange, op: Element, r: SoapRequest) {
        if (op.namespace != DEVICE_NS) violate("device service: ${op.localName} sent in namespace ${op.namespace}, expected $DEVICE_NS")
        when (op.localName) {
            "GetServices" -> {
                when (val include = r.text("IncludeCapability")) {
                    null -> violate("GetServices: IncludeCapability is required")
                    !in setOf("true", "false", "0", "1") -> violate("GetServices: IncludeCapability '$include' is not an xs:boolean")
                }
                respondSoap(x, servicesResponse())
            }
            "GetDeviceInformation" -> respondSoap(
                x,
                """<tds:GetDeviceInformationResponse><tds:Manufacturer>Conformance</tds:Manufacturer><tds:Model>FakeCam</tds:Model><tds:FirmwareVersion>1.0.0</tds:FirmwareVersion><tds:SerialNumber>FAKE0001</tds:SerialNumber><tds:HardwareId>fake</tds:HardwareId></tds:GetDeviceInformationResponse>""",
            )
            "GetHostname" -> respondSoap(
                x,
                """<tds:GetHostnameResponse><tds:HostnameInformation><tt:FromDHCP>false</tt:FromDHCP><tt:Name>fakecam</tt:Name></tds:HostnameInformation></tds:GetHostnameResponse>""",
            )
            "GetSystemDateAndTime" -> {
                if (r.first("UsernameToken") != null) violate("GetSystemDateAndTime is a pre-auth operation; sending credentials to it is pointless")
                val t = Instant.now().plus(clockSkew).atOffset(ZoneOffset.UTC)
                respondSoap(
                    x,
                    """<tds:GetSystemDateAndTimeResponse><tds:SystemDateAndTime><tt:DateTimeType>NTP</tt:DateTimeType><tt:DaylightSavings>false</tt:DaylightSavings><tt:UTCDateTime><tt:Time><tt:Hour>${t.hour}</tt:Hour><tt:Minute>${t.minute}</tt:Minute><tt:Second>${t.second}</tt:Second></tt:Time><tt:Date><tt:Year>${t.year}</tt:Year><tt:Month>${t.monthValue}</tt:Month><tt:Day>${t.dayOfMonth}</tt:Day></tt:Date></tt:UTCDateTime></tds:SystemDateAndTime></tds:GetSystemDateAndTimeResponse>""",
                )
            }
            else -> {
                violate("device service: unexpected operation ${op.localName}")
                respondFault(x, "Sender", listOf("ActionNotSupported"), "Action not supported")
            }
        }
    }

    private fun servicesResponse(): String {
        fun service(namespace: String, path: String, major: Int, minor: Int) =
            """<tds:Service><tds:Namespace>$namespace</tds:Namespace><tds:XAddr>http://$advertisedHost$path</tds:XAddr><tds:Version><tt:Major>$major</tt:Major><tt:Minor>$minor</tt:Minor></tds:Version></tds:Service>"""
        return buildString {
            append("<tds:GetServicesResponse>")
            append(service(DEVICE_NS, "/onvif/device_service", 24, 12))
            if (MediaService.MEDIA1 in media) append(service(MEDIA1_NS, "/onvif/media_service", 24, 12))
            if (MediaService.MEDIA2 in media) append(service(MEDIA2_NS, "/onvif/media2_service", 24, 12))
            append("</tds:GetServicesResponse>")
        }
    }

    // ---- Media2 ---------------------------------------------------------------------------

    private fun media2(x: HttpExchange, op: Element, r: SoapRequest) {
        if (op.namespace != MEDIA2_NS) violate("Media2: ${op.localName} sent in namespace ${op.namespace}, expected $MEDIA2_NS")
        if (notAuthorizedAsFault) {
            respondFault(x, "Sender", listOf("NotAuthorized"), "Sender not Authorized", status = 200)
            return
        }
        when (op.localName) {
            "GetProfiles" -> {
                val types = r.all("Type").map { it.text.toString().trim() }
                types.filter { it !in MEDIA2_CONFIGURATION_TYPES }
                    .forEach { violate("Media2 GetProfiles: Type '$it' is not in tr2:ConfigurationEnumeration") }
                // Without a matching Type the spec says: tokens and names only.
                val withEncoder = types.any { it == "VideoEncoder" || it == "All" }
                respondSoap(x, media2ProfilesResponse(withEncoder))
            }
            "GetStreamUri" -> {
                val token = r.text("ProfileToken")
                if (token == null) violate("Media2 GetStreamUri: ProfileToken is required")
                when (val protocol = r.text("Protocol")) {
                    null -> violate("Media2 GetStreamUri: Protocol is required")
                    !in MEDIA2_TRANSPORTS -> violate("Media2 GetStreamUri: Protocol '$protocol' is not a tr2:TransportProtocol")
                }
                if (!knownProfile(x, token)) return
                respondSoap(x, """<tr2:GetStreamUriResponse><tr2:Uri>${xml(streamUri(token!!))}</tr2:Uri></tr2:GetStreamUriResponse>""")
            }
            "GetSnapshotUri" -> {
                val token = r.text("ProfileToken")
                if (token == null) violate("Media2 GetSnapshotUri: ProfileToken is required")
                if (!knownProfile(x, token)) return
                if (token == SUB_PROFILE) {
                    respondFault(x, "Receiver", listOf("ActionNotSupported"), "Snapshot is not supported for this profile")
                    return
                }
                respondSoap(x, """<tr2:GetSnapshotUriResponse><tr2:Uri>${xml(snapshotUri())}</tr2:Uri></tr2:GetSnapshotUriResponse>""")
            }
            else -> {
                violate("Media2: unexpected operation ${op.localName}")
                respondFault(x, "Sender", listOf("ActionNotSupported"), "Action not supported")
            }
        }
    }

    private fun media2ProfilesResponse(withEncoder: Boolean): String {
        fun profile(token: String, encoding: String, width: Int, height: Int, bitrate: Int): String {
            val configurations = if (!withEncoder) "" else
                """<tr2:Configurations><tr2:VideoEncoder token="VideoEncoder${token.takeLast(3)}" GovLength="60" Profile="Main"><tt:Name>VideoEncoder${token.takeLast(3)}</tt:Name><tt:UseCount>1</tt:UseCount><tt:Encoding>$encoding</tt:Encoding><tt:Resolution><tt:Width>$width</tt:Width><tt:Height>$height</tt:Height></tt:Resolution><tt:RateControl ConstantBitRate="true"><tt:FrameRateLimit>30</tt:FrameRateLimit><tt:BitrateLimit>$bitrate</tt:BitrateLimit></tt:RateControl><tt:Quality>4.000000</tt:Quality></tr2:VideoEncoder></tr2:Configurations>"""
            return """<tr2:Profiles token="$token" fixed="true"><tr2:Name>$token</tr2:Name>$configurations</tr2:Profiles>"""
        }
        return "<tr2:GetProfilesResponse>" +
            profile(MAIN_PROFILE, "H265", 2688, 1520, 4096) +
            profile(SUB_PROFILE, "H264", 704, 480, 1024) +
            "</tr2:GetProfilesResponse>"
    }

    // ---- Media1 ---------------------------------------------------------------------------

    private fun media1(x: HttpExchange, op: Element, r: SoapRequest) {
        if (op.namespace != MEDIA1_NS) violate("Media1: ${op.localName} sent in namespace ${op.namespace}, expected $MEDIA1_NS")
        if (notAuthorizedAsFault) {
            respondFault(x, "Sender", listOf("NotAuthorized"), "Sender not Authorized", status = 200)
            return
        }
        when (op.localName) {
            "GetProfiles" -> {
                if (r.first("Type") != null) violate("Media1 GetProfiles takes no Type element")
                respondSoap(x, media1ProfilesResponse())
            }
            "GetStreamUri" -> {
                val setupAt = r.indexOf("StreamSetup")
                val tokenAt = r.indexOf("ProfileToken")
                when {
                    setupAt < 0 -> violate("Media1 GetStreamUri: StreamSetup is required")
                    tokenAt in 0 until setupAt -> violate("Media1 GetStreamUri: ProfileToken must follow StreamSetup (schema sequence)")
                }
                when (val stream = r.text("Stream")) {
                    null -> violate("Media1 GetStreamUri: StreamSetup/Stream is required")
                    !in setOf("RTP-Unicast", "RTP-Multicast") -> violate("Media1 GetStreamUri: Stream '$stream' is not a tt:StreamType")
                }
                when (val protocol = r.text("Protocol")) {
                    null -> violate("Media1 GetStreamUri: StreamSetup/Transport/Protocol is required")
                    !in setOf("UDP", "TCP", "RTSP", "HTTP") -> violate("Media1 GetStreamUri: Protocol '$protocol' is not a tt:TransportProtocol")
                }
                val token = r.text("ProfileToken")
                if (token == null) violate("Media1 GetStreamUri: ProfileToken is required")
                if (!knownProfile(x, token)) return
                respondSoap(x, media1UriResponse("GetStreamUriResponse", streamUri(token!!)))
            }
            "GetSnapshotUri" -> {
                val token = r.text("ProfileToken")
                if (token == null) violate("Media1 GetSnapshotUri: ProfileToken is required")
                if (!knownProfile(x, token)) return
                if (token == SUB_PROFILE) {
                    respondFault(x, "Receiver", listOf("ActionNotSupported"), "Snapshot is not supported for this profile")
                    return
                }
                respondSoap(x, media1UriResponse("GetSnapshotUriResponse", snapshotUri()))
            }
            else -> {
                violate("Media1: unexpected operation ${op.localName}")
                respondFault(x, "Sender", listOf("ActionNotSupported"), "Action not supported")
            }
        }
    }

    private fun media1ProfilesResponse(): String {
        // Media1's VideoEncoding has no H265, so, like the real camera, the main profile's
        // encoder carries no Encoding and no Resolution.
        fun encoder(token: String, encoding: String?, width: Int?, height: Int?): String {
            val details = if (encoding == null) "" else
                """<tt:Encoding>$encoding</tt:Encoding><tt:Resolution><tt:Width>$width</tt:Width><tt:Height>$height</tt:Height></tt:Resolution>"""
            return """<tt:VideoEncoderConfiguration token="$token"><tt:Name>$token</tt:Name><tt:UseCount>1</tt:UseCount>$details<tt:Quality>4.000000</tt:Quality></tt:VideoEncoderConfiguration>"""
        }
        return "<trt:GetProfilesResponse>" +
            """<trt:Profiles token="$MAIN_PROFILE" fixed="true"><tt:Name>$MAIN_PROFILE</tt:Name>${encoder("VideoEncoder000", null, null, null)}</trt:Profiles>""" +
            """<trt:Profiles token="$SUB_PROFILE" fixed="true"><tt:Name>$SUB_PROFILE</tt:Name>${encoder("VideoEncoder001", "H264", 704, 480)}</trt:Profiles>""" +
            "</trt:GetProfilesResponse>"
    }

    private fun media1UriResponse(name: String, uri: String) =
        """<trt:$name><trt:MediaUri><tt:Uri>${xml(uri)}</tt:Uri><tt:InvalidAfterConnect>false</tt:InvalidAfterConnect><tt:InvalidAfterReboot>false</tt:InvalidAfterReboot><tt:Timeout>PT60S</tt:Timeout></trt:MediaUri></trt:$name>"""

    // ---- Shared -----------------------------------------------------------------------------

    private fun knownProfile(x: HttpExchange, token: String?): Boolean {
        if (token == MAIN_PROFILE || token == SUB_PROFILE) return true
        respondFault(x, "Sender", listOf("InvalidArgVal", "NoProfile"), "The requested profile token does not exist.")
        return false
    }

    private fun streamUri(token: String) =
        "rtsp://$advertisedHost:554/cam/realmonitor?channel=1&subtype=${if (token == MAIN_PROFILE) 0 else 1}&unicast=true&proto=Onvif"

    // The port is this server's: the client rewrites the host but must keep the camera's port.
    private fun snapshotUri() = "http://$advertisedHost:$port$SNAPSHOT_PATH?channel=1&subtype=0"

    /**
     * The snapshot resource: a plain HTTP GET behind the same Digest (or Basic) challenge as the
     * SOAP services, as the ONVIF Streaming Specification requires. WS-Security means nothing
     * here, so a client must answer the HTTP challenge, and must not answer it with Basic when
     * Digest was offered.
     */
    private fun snapshot(x: HttpExchange) {
        if (x.requestMethod != "GET") {
            violate("snapshot: ${x.requestMethod} instead of GET")
            respond(x, 405, "text/plain", "")
            return
        }
        if (credentials != null && !authorized(x, SNAPSHOT_PATH)) {
            challenge(x)
            return
        }
        snapshotGets.incrementAndGet()
        x.responseHeaders.add("Content-Type", "image/jpeg")
        x.sendResponseHeaders(200, SNAPSHOT_JPEG.size.toLong())
        x.responseBody.use { it.write(SNAPSHOT_JPEG) }
    }

    // ---- WS-Security UsernameToken --------------------------------------------------------------

    /**
     * True if the request carries a UsernameToken whose PasswordDigest verifies against this
     * device's credentials. Structural mistakes are violations; a wrong username or password is
     * merely not accepted.
     */
    private fun verifyUsernameToken(r: SoapRequest, path: String): Boolean {
        val (user, pass) = credentials ?: return false
        val token = r.first("UsernameToken") ?: return false
        if (token.namespace != WSSE_NS) violate("$path: UsernameToken in namespace ${token.namespace}, expected $WSSE_NS")
        val children = r.children(token)
        val username = children.firstOrNull { it.localName == "Username" }?.text?.toString()?.trim()
        val password = children.firstOrNull { it.localName == "Password" }
        val nonce = children.firstOrNull { it.localName == "Nonce" }?.text?.toString()?.trim()
        val created = children.firstOrNull { it.localName == "Created" }
        if (username == null || password == null || nonce == null || created == null) {
            violate("$path: UsernameToken lacks Username, Password, Nonce or Created")
            return false
        }
        if (password.attributes["Type"] != PASSWORD_DIGEST_TYPE) {
            violate("$path: Password Type is '${password.attributes["Type"]}', expected PasswordDigest")
        }
        if (created.namespace != WSU_NS) violate("$path: Created in namespace ${created.namespace}, expected $WSU_NS")
        val createdText = created.text.toString().trim()
        val createdAt = try {
            Instant.parse(createdText)
        } catch (_: Exception) {
            violate("$path: Created '$createdText' is not an xs:dateTime in UTC")
            return false
        }
        val drift = Duration.between(createdAt, Instant.now().plus(clockSkew)).abs()
        if (drift > Duration.ofMinutes(5)) {
            violate("$path: Created is ${drift.seconds}s from the device clock; devices reject tokens more than a few minutes off")
        }
        val nonceBytes = try {
            java.util.Base64.getDecoder().decode(nonce)
        } catch (_: IllegalArgumentException) {
            violate("$path: Nonce is not Base64")
            return false
        }
        if (username != user) return false
        val expected = MessageDigest.getInstance("SHA-1").run {
            update(nonceBytes)
            update(createdText.encodeToByteArray())
            update(pass.encodeToByteArray())
            digest()
        }
        return java.util.Base64.getEncoder().encodeToString(expected) == password.text.toString().trim()
    }

    // ---- HTTP authentication --------------------------------------------------------------------

    private fun authorized(x: HttpExchange, path: String): Boolean {
        val (user, pass) = credentials ?: return true
        val all = x.requestHeaders["Authorization"].orEmpty()
        if (all.size > 1) violate("$path: ${all.size} Authorization headers in one request: ${all.map { it.substringBefore(' ') }}")
        val header = all.firstOrNull() ?: return false
        val scheme = offeredSchemes.firstOrNull { it.equals(header.substringBefore(' '), ignoreCase = true) }
        if (scheme == null) {
            // Credentials in a scheme the device never offered: at best wasted, at worst (Basic
            // over plain HTTP) the password in clear text.
            violate("$path: Authorization uses ${header.substringBefore(' ')} after a $offeredSchemes challenge")
            return false
        }
        if (scheme == "Basic") {
            if ("Digest" in offeredSchemes) {
                // The password in clear text to a device that would have taken Digest.
                violate("$path: Authorization uses Basic although Digest was offered")
            }
            if (digestAfterBasic) {
                offeredSchemes = listOf("Digest")
                return false
            }
            val decoded = java.util.Base64.getDecoder().decode(header.substring("Basic ".length).trim()).decodeToString()
            return decoded == "$user:$pass"
        }
        val p = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]*))""")
            .findAll(header.substring("Digest ".length))
            .associate { m -> m.groupValues[1] to m.groupValues[2].ifEmpty { m.groupValues[3] } }
        if (p["realm"] != realm) violate("$path: Digest realm '${p["realm"]}' is not the challenged realm")
        if (p["nonce"] != nonce) violate("$path: Digest nonce is not the challenged nonce")
        // RFC 7616: the digest-uri is the request-target, query string included.
        val requestTarget = x.requestURI.rawPath + (x.requestURI.rawQuery?.let { "?$it" } ?: "")
        val uri = p["uri"]
        if (uri != requestTarget) violate("$path: Digest uri '$uri' is not the request-target '$requestTarget'")
        if (p["username"] != user) return false
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("${x.requestMethod}:$uri")
        val expected = when (val qop = p["qop"]) {
            null -> md5("$ha1:$nonce:$ha2")
            "auth" -> {
                if (p["nc"] == null || p["cnonce"] == null) violate("$path: Digest with qop=auth needs nc and cnonce")
                md5("$ha1:$nonce:${p["nc"]}:${p["cnonce"]}:auth:$ha2")
            }
            else -> {
                violate("$path: Digest qop '$qop' was not offered")
                return false
            }
        }
        // A mismatch is wrong credentials, not a protocol violation; the correct-credential tests
        // would fail loudly if the client computed the digest wrongly.
        return expected.equals(p["response"], ignoreCase = true)
    }

    private fun challenge(x: HttpExchange) {
        challenges.incrementAndGet()
        for (scheme in offeredSchemes) {
            val value = if (scheme == "Basic") "Basic realm=\"$realm\""
            else "Digest realm=\"$realm\", nonce=\"$nonce\", qop=\"auth\", algorithm=MD5"
            x.responseHeaders.add("WWW-Authenticate", value)
        }
        respond(x, 401, "text/plain", "Unauthorized")
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    // ---- Responses --------------------------------------------------------------------------

    private fun respond(x: HttpExchange, status: Int, contentType: String, body: String) {
        val bytes = body.encodeToByteArray()
        x.responseHeaders.add("Content-Type", contentType)
        x.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        x.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
    }

    private fun respondSoap(x: HttpExchange, body: String, status: Int = 200) = respond(
        x,
        status,
        "application/soap+xml; charset=utf-8",
        """<?xml version="1.0" encoding="UTF-8"?><s:Envelope xmlns:s="$SOAP12_NS" xmlns:tt="$SCHEMA_NS" xmlns:tds="$DEVICE_NS" xmlns:trt="$MEDIA1_NS" xmlns:tr2="$MEDIA2_NS" xmlns:ter="http://www.onvif.org/ver10/error"><s:Body>$body</s:Body></s:Envelope>""",
    )

    private fun respondFault(x: HttpExchange, code: String, subcodes: List<String>, reason: String, status: Int = faultStatus) {
        val nested = subcodes.foldRight("") { sub, inner -> "<s:Subcode><s:Value>ter:$sub</s:Value>$inner</s:Subcode>" }
        respondSoap(
            x,
            """<s:Fault><s:Code><s:Value>s:$code</s:Value>$nested</s:Code><s:Reason><s:Text xml:lang="en">${xml(reason)}</s:Text></s:Reason></s:Fault>""",
            status,
        )
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/** One element of a request, in document order. */
internal class Element(val localName: String, val namespace: String, val depth: Int) {
    val text = StringBuilder()
    val attributes = mutableMapOf<String, String>()
}

internal class SoapRequest(val elements: List<Element>) {
    val envelope: Element? get() = elements.firstOrNull()

    /** The first child of the SOAP Body: the operation. */
    val operation: Element?
        get() {
            val body = elements.indexOfFirst { it.localName == "Body" && it.namespace == SOAP12_NS }
            if (body < 0) return null
            return elements.drop(body + 1).firstOrNull { it.depth == elements[body].depth + 1 }
        }

    fun first(localName: String): Element? = elements.firstOrNull { it.localName == localName }

    /** The elements nested anywhere inside [parent]. */
    fun children(parent: Element): List<Element> =
        elements.drop(elements.indexOf(parent) + 1).takeWhile { it.depth > parent.depth }
    fun all(localName: String): List<Element> = elements.filter { it.localName == localName }
    fun text(localName: String): String? = first(localName)?.text?.toString()?.trim()
    fun indexOf(localName: String): Int = elements.indexOfFirst { it.localName == localName }
}

internal fun parseSoapRequest(body: String): SoapRequest {
    val elements = mutableListOf<Element>()
    val open = ArrayDeque<Element>()
    xmlStreaming.newReader(body).use { reader ->
        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    val element = Element(reader.localName, reader.namespaceURI, open.size)
                    for (i in 0 until reader.attributeCount) {
                        element.attributes[reader.getAttributeLocalName(i)] = reader.getAttributeValue(i)
                    }
                    elements += element
                    open.addLast(element)
                }
                EventType.END_ELEMENT -> open.removeLast()
                EventType.TEXT, EventType.CDSECT -> open.lastOrNull()?.text?.append(reader.text)
                else -> Unit
            }
        }
    }
    return SoapRequest(elements)
}
