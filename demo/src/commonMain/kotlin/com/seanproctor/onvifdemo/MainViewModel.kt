package com.seanproctor.onvifdemo

import androidx.compose.runtime.mutableStateOf
import com.ivanempire.lighthouse.LighthouseClient
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.digest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.http.Url
import io.ktor.utils.io.core.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class MainViewModel(
    private val lighthouseClient: LighthouseClient,
    private val onvifDiscoveryManager: OnvifDiscoveryManager,
) : ViewModel() {

    val address = mutableStateOf("")
    val login = mutableStateOf("")
    val password = mutableStateOf("")
    val snapshotUri = mutableStateOf<String?>(null)

    private var device: OnvifDevice? = null

    private val _explanationText = MutableStateFlow<String?>(null)
    val explanationText = _explanationText.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText = _errorText.asStateFlow()

    private val _image = MutableStateFlow<ByteArray?>(null)
    val image = _image.asStateFlow()

    val discoveredDevices: Flow<List<CameraInformation>> = flow {
        val cachedOnvifDevices = mutableMapOf<String, OnvifCachedDevice>()
        val friendlyNameMap = mutableMapOf<String, String>()
        combine(
            onvifDiscoveryManager.discoverDevices(2),
            lighthouseClient.discoverDevices(),
        ) { onvifDevices, ssdpDevices ->
            onvifDevices.mapNotNull { onvifDevice ->
                var friendlyName = onvifDevice.id
                val cachedOnvifDevice = cachedOnvifDevices[onvifDevice.id]
                val endpoint = cachedOnvifDevice?.endpoint
                    ?: onvifDevice.addresses
                        .firstOrNull { OnvifDevice.isReachableEndpoint(it) }
                        ?.also { endpoint ->
                            cachedOnvifDevices[onvifDevice.id] =
                                OnvifCachedDevice(
                                    onvifDevice.addresses.map { Url(it).host },
                                    endpoint
                                )
                        }
                    ?: return@mapNotNull null
                val ssdpFriendlyName = friendlyNameMap.getOrElse(onvifDevice.id) {
                    ssdpDevices.firstNotNullOfOrNull { ssdpDevice ->
                        if (onvifDevice.addresses.any { Url(it).host == ssdpDevice.location.host }) {
                            val detailedDevice = lighthouseClient.retrieveDescription(ssdpDevice)
                            friendlyNameMap[onvifDevice.id] = detailedDevice.friendlyName
                            detailedDevice.friendlyName
                        } else {
                            null
                        }
                    }
                }
                if (ssdpFriendlyName != null) {
                    friendlyName = ssdpFriendlyName
                }
                CameraInformation(friendlyName, onvifDevice.id, endpoint)
            }
        }
            .collect {
                emit(it)
            }
    }
        .flowOn(Dispatchers.IO)

    fun connectClicked() {
        val address = address.value.trim()
        val login = login.value.trim()
        val password = password.value.trim()

        if (address.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Get camera services
                    Napier.d("Requesting device: \"$address\" \"$login\" \"$password\"")
                    val url =
                        if (address.contains("://")) address
                        else "http://$address/onvif/device_service"
                    val device = OnvifDevice.requestDevice(url, login, password, true)
                    this@MainViewModel.device = device

                    // Display camera specs
                    Napier.d("Getting device information")
                    val deviceInformation = device.getDeviceInformation()
                    _explanationText.value = deviceInformation.toString()

                    // Get media profiles to find which ones are streams/snapshots
                    Napier.d("Getting device profiles")
                    val profiles = device.getProfiles()

                    profiles.firstOrNull { it.canSnapshot() }?.let {
                        Napier.d("Getting snapshot URI")
                        device.getSnapshotURI(it).let { uri ->
                            snapshotUri.value = uri
                        }
                    }
                } catch (e: Exception) {
                    _errorText.value = "Error: ${e.message}"
                    Napier.e("error", e)
                }
            }
        } else {
            _errorText.value = "Please enter an IP Address login and password"
        }
    }

    fun clearErrorText() {
        _errorText.value = null
    }

    fun getSnapshot() {
        val username = login.value
        val password = password.value
        val url = snapshotUri.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            HttpClient {
                if (username.isNotBlank() && password.isNotBlank()) {
                    install(Auth) {
                        basic {
                            credentials {
                                BasicAuthCredentials(username = username, password = password)
                            }
                        }
                        digest {
                            credentials {
                                DigestAuthCredentials(username = username, password = password)
                            }
                        }
                    }
                }
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.BODY
                }
            }.use { client ->
                Napier.d("Getting snapshot: $url")
                try {
                    val response = client.get(url)
                    if (response.status.value in 200..299) {
                        Napier.d("Got image")
                        _image.value = response.body()
                    } else {
                        Napier.d("Got an error: ${response.status}")
                        _errorText.value = response.status.toString()
                    }
                } catch (e: Exception) {
                    Napier.d("Got an error: ${e.message}", e)
                    _errorText.value = e.message ?: "Unknown error"
                }
            }
        }
    }

    fun clearSnapshot() {
        _image.value = null
    }
}

data class CameraInformation(val friendlyName: String?, val id: String, val host: String)

data class OnvifCachedDevice(val hosts: List<String>, val endpoint: String)