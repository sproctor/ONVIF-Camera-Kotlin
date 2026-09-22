# ONVIF Camera Kotlin
Kotlin Multiplatform library for ONVIF cameras on Android and JVM: WS-Discovery on the local
network, device information, media profiles, and stream and snapshot URIs.

Install with Gradle (must have mavenCentral in repositories):

```kotlin
implementation("com.seanproctor:onvifcamera:<VERSION>")
```

## Discover cameras on the local network

```kotlin
val discovery = OnvifDiscoveryManager()          // JVM
val discovery = OnvifDiscoveryManager(context)   // Android: needs a Context for the multicast lock

// The flow is cold. Collecting it opens a socket, sends the WS-Discovery probe on the
// SOAP-over-UDP retransmission schedule and keeps listening until the collector is
// cancelled. Each emission is every device found so far, in the order they answered.
withTimeoutOrNull(5.seconds) {
    discovery.discoverDevices()
        .catch { e -> println("Discovery failed: $e") }   // the socket could not be opened or read
        .collect { devices -> println("${devices.size} camera(s) so far") }
}
```

The flow never completes on its own, because cameras answer whenever they like. Bound a run with
a timeout as above, or cancel the collecting coroutine when the user leaves the screen.

Each `DiscoveredOnvifDevice` lists candidate `addresses` for the device service. Cameras
sometimes advertise a stale address, so the URL on the address the reply actually came from is
listed first; `OnvifDevice.isReachableEndpoint(url)` checks one without credentials.

On Android the library's manifest already declares `CHANGE_WIFI_MULTICAST_STATE` and `INTERNET`.

## Connect to a camera and read its information

```kotlin
val device = OnvifDevice.requestDevice("http://IP_ADDRESS:PORT/onvif/device_service", "login", "pwd")
val deviceInfo = device.getDeviceInformation()
```

Leave the credentials out for a camera that does not require them.

## Retrieve the stream and snapshot URIs

```kotlin
val profiles = device.getProfiles()

// Any profile with a video encoder can be asked for a stream URI. `encoding` is the codec as
// the camera spells it (H264, H265, JPEG, ...); it is null for an audio-only profile.
val profile = profiles.first { it.encoding != null }
val streamUri = device.getStreamURI(profile)

// Snapshots are JPEG whatever the profile's codec. Whether the camera offers one at all is
// the camera's answer: a device without snapshot support replies with a fault.
val snapshotUri = try {
    device.getSnapshotURI(profile)
} catch (e: OnvifException) {
    null
}
```

## Errors

Every failure the library raises about a device is an `OnvifException`:

| Exception | Meaning |
|---|---|
| `OnvifUnauthorized` | The camera answered 401: credentials missing or wrong |
| `OnvifForbidden` | The camera answered 403: credentials right, operation not permitted |
| `OnvifInvalidResponse` | Any other non-2xx status |
| `OnvifServiceUnavailable` | The camera does not offer the service an operation needs; `namespace` says which |

Network failures surface as the platform's `IOException`.

## References

http://www.onvif.org/ver10/device/wsdl/devicemgmt.wsdl
