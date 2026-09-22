package com.seanproctor.onvifdemo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.OnvifException
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val onvifDiscoveryManager: OnvifDiscoveryManager,
    private val logger: OnvifLogger,
) : ViewModel() {

    var address by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var snapshotUri by mutableStateOf<String?>(null)
    var streamUri by mutableStateOf<String?>(null)

    private var device: OnvifDevice? = null

    private val _explanationText = MutableStateFlow<String?>(null)
    val explanationText = _explanationText.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText = _errorText.asStateFlow()

    private val _image = MutableStateFlow<ByteArray?>(null)
    val image = _image.asStateFlow()

    private val cachedCameras = mutableMapOf<String, CameraInformation>()
    fun discoverDevices(): Flow<List<CameraInformation>> =
        onvifDiscoveryManager.discoverDevices()
            .map { onvifDevices ->
                onvifDevices.mapNotNull { onvifDevice ->
                    // TODO: make this an async call
                    cachedCameras[onvifDevice.id]
                        ?: onvifDevice.addresses
                            .firstOrNull {
                                try {
                                    OnvifDevice.isReachableEndpoint(it)
                                } catch (_: Throwable) {
                                    false
                                }
                            }
                            ?.let { endpoint ->
                                CameraInformation(
                                    id = onvifDevice.id,
                                    friendlyName = OnvifDevice.getHostname(endpoint, logger),
                                    host = endpoint,
                                )
                            }
                            ?.also { info ->
                                cachedCameras[onvifDevice.id] = info
                            }
                }
            }
            // Discovery fails the flow if the socket cannot be opened or read; surface it
            // rather than let it escape the LaunchedEffect collecting this.
            .catch { e ->
                logger.error("Discovery failed", e)
                _errorText.value = "Discovery failed: ${e.message}"
            }
            .onCompletion {
                logger.debug("Stopped scanning")
            }
            .flowOn(Dispatchers.IO)

    fun connectClicked() {
        val address = address.trim()
        val username = username.trim()
        val password = password.trim()

        if (address.isNotEmpty()) {
            // Whatever the previous camera offered must not survive into this connection: a
            // lookup that fails or is skipped below would otherwise leave the old URI in place,
            // and getSnapshot() would fetch from the old camera with the new credentials.
            streamUri = null
            snapshotUri = null
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Get camera services
                    Napier.d("Requesting device: \"$address\" \"$username\" \"$password\"")
                    val url =
                        if (address.contains("://")) address
                        else "http://$address/onvif/device_service"
                    val device = OnvifDevice.requestDevice(url, username, password, logger)
                    // The handle owns an HTTP client; release the previous camera's.
                    this@MainViewModel.device?.close()
                    this@MainViewModel.device = device

                    // Display camera specs
                    Napier.d("Getting device information")
                    val deviceInformation = device.getDeviceInformation()
                    _explanationText.value = deviceInformation.toString()

                    // Get media profiles to find which ones are streams/snapshots
                    Napier.d("Getting device profiles")
                    val profiles = device.getProfiles()

                    // Any profile with a video encoder can be asked for a stream. Whether it
                    // can also supply a snapshot is the device's answer, not the profile's.
                    profiles.firstOrNull { it.encoding != null }?.let { profile ->
                        Napier.d("Getting stream URI")
                        streamUri = device.getStreamURI(profile)
                        Napier.d("Getting snapshot URI")
                        try {
                            snapshotUri = device.getSnapshotURI(profile)
                        } catch (e: OnvifException) {
                            Napier.w("No snapshot for profile ${profile.token}", e)
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

    override fun onCleared() {
        device?.close()
        super.onCleared()
    }

    fun getSnapshot() {
        val device = device ?: return
        val url = snapshotUri ?: return

        // Fetched through the device's own client, so the camera's Digest challenge is answered
        // with the connection's credentials; no second HTTP client to configure (or misconfigure).
        viewModelScope.launch(Dispatchers.IO) {
            Napier.d("Getting snapshot: $url")
            try {
                _image.value = device.getSnapshot(url)
                Napier.d("Got image")
            } catch (e: Exception) {
                Napier.d("Got an error: ${e.message}", e)
                _errorText.value = e.message ?: "Unknown error"
            }
        }
    }

    fun clearSnapshot() {
        _image.value = null
    }
}

data class CameraInformation(val friendlyName: String?, val id: String, val host: String)
