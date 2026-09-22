# ONVIF Camera Kotlin
Kotlin Multiplatform library for ONVIF cameras on Android and JVM: WS-Discovery on the local
network, device information, media profiles, and stream and snapshot URIs.

Install with Gradle (must have mavenCentral in repositories):

```kotlin
implementation("com.seanproctor:onvifcamera:<VERSION>")
```

## Discover cameras on the local network

```kotlin
val discovery = OnvifDiscoveryManager()
// On Android: OnvifDiscoveryManager(context), which needs the Context for the multicast lock.

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
An app that targets Android 17 (API 37) or higher must also declare
`android.permission.ACCESS_LOCAL_NETWORK` in its own manifest and request it at runtime before
collecting the flow: Android 17 blocks local-network UDP for those apps until the user grants it,
and discovery then fails with an `IOException`. The library does not declare that permission
itself, because Android grants local-network access implicitly to apps targeting API 36 or lower
and advises against declaring it there. The demo's `MainActivity` shows the request.

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
// the camera spells it (H264, H265, JPEG, ...); it is null for an audio-only profile, so a
// device may have no profile to stream from.
val profile = profiles.firstOrNull { it.encoding != null }
    ?: error("The device has no video profile")
val streamUri = device.getStreamURI(profile)

// Snapshots are JPEG whatever the profile's codec. Whether the camera offers one at all is
// the camera's answer: a device without snapshot support replies with a SOAP fault.
val snapshotUri = try {
    device.getSnapshotURI(profile)
} catch (e: OnvifFault) {
    null
}
```

## Errors

Every failure the library raises about a device is an `OnvifException`:

| Exception | Meaning |
|---|---|
| `OnvifUnauthorized` | The camera answered 401, or with a SOAP fault whose subcode is `NotAuthorized`: credentials missing or wrong |
| `OnvifForbidden` | The camera answered 403: credentials right, operation not permitted |
| `OnvifFault` | The camera rejected the operation with any other SOAP fault; `code`, `subcodes`, `reason` and `detail` say why |
| `OnvifInvalidResponse` | Any other non-2xx status without a fault |
| `OnvifServiceUnavailable` | The camera does not offer the service an operation needs; `namespace` says which |

Network failures surface as the platform's `IOException`.

## References

http://www.onvif.org/ver10/device/wsdl/devicemgmt.wsdl
