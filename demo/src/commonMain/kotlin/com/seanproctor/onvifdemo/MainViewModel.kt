package com.seanproctor.onvifdemo

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.customDigest
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

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

    private val _discoveredDevices = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    fun startDiscovery() {
        discoverJob?.cancel()
        Napier.i { "Starting discovery" }
        discoverJob = viewModelScope.launch(Dispatchers.IO) {
            OnvifDevice.discoverDevices {
                Napier.i("Found device: $it")
                _discoveredDevices.value += it.address to it.uri
            }
        }
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