package com.seanproctor.onvifdemo

import androidx.compose.runtime.mutableStateOf
import com.ivanempire.lighthouse.LighthouseClient
import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.customDigest
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.http.Url
import io.ktor.utils.io.core.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class MainViewModel(
    private val lighthouseClient: LighthouseClient
) : ViewModel() {

    private var discoverJob: Job? = null

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

    private val onvifDevices = MutableStateFlow<Map<String, DiscoveredOnvifDevice>>(emptyMap())
    private val ssdpDevices = MutableStateFlow<Map<String, CameraInformation>>(emptyMap())

    val discoveredDevices: Flow<List<CameraInformation>> = combine(onvifDevices, ssdpDevices) { onvifDevices, ssdpDevices ->
        onvifDevices.entries.map { onvifEntry ->
            val ssdpDevice = ssdpDevices.entries.firstOrNull() { ssdpEntry ->
                onvifEntry.value.addresses.any { Url(it).host == ssdpEntry.value.host }
            }?.value
            val friendlyName = ssdpDevice?.friendlyName
            CameraInformation(friendlyName, onvifEntry.value.id, onvifEntry.key)
        }
    }

    fun startDiscovery() {
        discoverJob?.cancel()
        Napier.i { "Starting discovery" }
        discoverJob = viewModelScope.launch(Dispatchers.IO) {
            OnvifDevice.discoverDevices { device ->
                viewModelScope.launch(Dispatchers.IO) {
                    Napier.i("Found device: $device")
                    device.addresses
                        .firstOrNull { OnvifDevice.isReachableEndpoint(it) }
                        ?.let {
                            onvifDevices.value = onvifDevices.value + (Url(it).host to device)
                        }
                }
            }
        }
        lighthouseClient.discoverDevices()
            .onEach { ssdpDevices ->
                ssdpDevices.forEach {
                    if (!this.ssdpDevices.value.containsKey(it.uuid)) {
                        try {
                            val detailedDevice = lighthouseClient.retrieveDescription(it)
                            val info = CameraInformation(
                                friendlyName = detailedDevice.friendlyName,
                                id = it.uuid,
                                host = it.location.host,
                            )
                            this.ssdpDevices.value = this.ssdpDevices.value + (it.uuid to info)
                        } catch (e: Throwable) {
                            // Ignore exceptions
                        }
                    }
                }
            }
            .launchIn(viewModelScope + Dispatchers.IO)
    }

    fun stopDiscovery() {
        discoverJob?.cancel()
        discoverJob = null
    }

    fun connectClicked() {
        val address = address.value.trim()
        val login = login.value.trim()
        val password = password.value.trim()

        if (address.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Get camera services
                    Napier.d("Requesting device: \"$address\" \"$login\" \"$password\"")
                    val device = OnvifDevice.requestDevice(address, login, password, true)
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
                        customDigest {
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
                    _errorText.value = e.message ?: "Uknown error"
                }
            }
        }
    }

    fun clearSnapshot() {
        _image.value = null
    }
}

data class CameraInformation(val friendlyName: String?, val id: String, val host: String)
