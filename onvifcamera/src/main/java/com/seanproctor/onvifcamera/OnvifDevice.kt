package com.seanproctor.onvifcamera


import android.util.Log
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.seanproctor.onvifcamera.OnvifCommands.deviceInformationCommand
import com.seanproctor.onvifcamera.OnvifCommands.getSnapshotURICommand
import com.seanproctor.onvifcamera.OnvifCommands.getStreamURICommand
import com.seanproctor.onvifcamera.OnvifCommands.profilesCommand
import com.seanproctor.onvifcamera.OnvifCommands.servicesCommand
import com.seanproctor.onvifcamera.OnvifXmlParser.parseDeviceInformationResponse
import com.seanproctor.onvifcamera.OnvifXmlParser.parseProfilesResponse
import com.seanproctor.onvifcamera.OnvifXmlParser.parseServicesResponse
import com.seanproctor.onvifcamera.OnvifXmlParser.parseStreamURIXML
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


/**
 * Informs us of what and where to send to the device
 */
internal enum class OnvifRequestType {

    GetServices,
    GetDeviceInformation,
    GetProfiles,
    GetStreamURI,
    GetSnapshotURI;

    fun namespace(): String =
        when (this) {
            GetServices, GetDeviceInformation -> "http://www.onvif.org/ver10/device/wsdl"
            GetProfiles, GetStreamURI, GetSnapshotURI -> "http://www.onvif.org/ver20/media/wsdl"
        }
}

/**
 * @author Remy Virin on 04/03/2018.
 * This class represents an ONVIF device and contains the methods to interact with it
 * (getDeviceInformation, getProfiles and getStreamURI).
 * @param hostname The IP address of the camera
 * @param username the username to login on the camera
 * @param password the password to login on the camera
 * @param namespaceMap a mapping of SOAP namespaces to URI paths
 */
public class OnvifDevice internal constructor(
    public val hostname: String,
    public val username: String?,
    public val password: String?,
    public val namespaceMap: Map<String, String>,
    public val debug: Boolean,
) {

    public suspend fun getDeviceInformation(): OnvifDeviceInformation {
        val path = pathForRequest(OnvifRequestType.GetDeviceInformation)
        val response = execute(hostname, path, deviceInformationCommand, username, password, debug)
        return parseDeviceInformationResponse(response)
    }

    public suspend fun getProfiles(): List<MediaProfile> {
        val path = pathForRequest(OnvifRequestType.GetProfiles)
        val response = execute(hostname, path, profilesCommand, username, password, debug)
        return parseProfilesResponse(response)
    }

    public suspend fun getStreamURI(
        profile: MediaProfile,
        addCredentials: Boolean = false
    ): String {
        val path = pathForRequest(OnvifRequestType.GetStreamURI)
        val response =
            execute(hostname, path, getStreamURICommand(profile), username, password, debug)
        val uri = parseStreamURIXML(response)
        return if (addCredentials) {
            appendCredentials(uri)
        } else {
            uri
        }
    }

    public suspend fun getSnapshotURI(profile: MediaProfile): String {
        val path = pathForRequest(OnvifRequestType.GetSnapshotURI)
        val response =
            execute(hostname, path, getSnapshotURICommand(profile), username, password, debug)
        return parseStreamURIXML(response)
    }

    private fun pathForRequest(requestType: OnvifRequestType): String {
        return namespaceMap[requestType.namespace()] ?: "/onvif/device_service"
    }

    /**
     * Util method to append the credentials to the rtsp URI
     * Working if the camera is behind a firewall.
     * @param original the URI to modify
     * @return the URI with the credentials
     */
    private fun appendCredentials(original: String): String {
        // Do nothing if we don't have a username and password
        if (username == null || password == null) {
            return original
        }

        val uri = URI(original)

        val port = if (uri.port > 0) {
            uri.port.toString()
        } else {
            ""
        }
        val query = if (uri.query.isNotBlank()) {
            "?" + uri.query
        } else {
            ""
        }

        return uri.scheme + "://" + username + ":" + password + "@" +
                uri.host + port + uri.path + query
    }

    public companion object {
        public suspend fun requestDevice(
            hostname: String,
            username: String?,
            password: String?,
            debug: Boolean = false,
        ): OnvifDevice {
            val result =
                execute(
                    hostname,
                    "/onvif/device_service",
                    servicesCommand,
                    username,
                    password,
                    debug
                )
            val namespaceMap = parseServicesResponse(result)
            return OnvifDevice(hostname, username, password, namespaceMap, debug)
        }

        internal suspend fun execute(
            hostname: String,
            urlPath: String,
            command: String,
            username: String?,
            password: String?,
            debug: Boolean,
        ): InputStream {
            return withContext(Dispatchers.IO) {
                val credentials = Credentials(username, password)
                val authenticator = DispatchingAuthenticator.Builder()
                    .with("digest", DigestAuthenticator(credentials))
                    .with("basic", BasicAuthenticator(credentials))
                    .build()
                val client = OkHttpClient.Builder()
                    .authenticator(authenticator)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .let {
                        if (debug) {
                            it.addInterceptor(
                                HttpLoggingInterceptor(HttpLogger()).apply {
                                    level = HttpLoggingInterceptor.Level.BODY
                                }
                            )
                        } else {
                            it
                        }
                    }
                    .build()

                val reqMediaType = "application/soap+xml; charset=utf-8;".toMediaTypeOrNull()

                val reqBody = command.toRequestBody(reqMediaType)

                /* Request to ONVIF device */
                val url = "http://$hostname$urlPath"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "text/xml; charset=utf-8")
                    .post(reqBody)
                    .build()

                /* Response from ONVIF device */
                suspendCoroutine { cont ->
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            cont.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {

                            if (response.isSuccessful) {
                                response.body?.byteStream()?.let { cont.resume(it) }
                                    ?: cont.resumeWithException(OnvifInvalidResponse("Response has empty body"))
                            } else {
                                cont.resumeWithException(
                                    when (response.code) {
                                        401 -> OnvifUnauthorized("Unauthorized")
                                        403 -> OnvifForbidden("Forbidden")
                                        else -> OnvifInvalidResponse("Invalid response from device")
                                    }
                                )
                            }
                        }
                    })
                }
            }
        }
    }
}

private class HttpLogger : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        Log.v("REQUEST", message)
    }
}