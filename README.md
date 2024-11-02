# ONVIF Camera Kotlin
Kotlin MPP implementation of ONVIF discovery for cameras on Android and JVM.

Install with Gradle (must have mavenCentral in repositories):

```kotlin
implementation("com.seanproctor:onvifcamera:<VERSION>")
```

## Connect to an Onvif camera and information

```kotlin
val device = OnvifDevice.requestDevice("IP_ADDRESS:PORT", "login", "pwd")
val deviceInfo = device.getDeviceInformation()
```
## Retrieve the stream URI

```kotlin
val device = OnvifDevice.requestDevice("IP_ADDRESS:PORT", "login", "pwd")

// Get media profiles to find which ones are streams/snapshots
val profiles = device.getProfiles()

val streamUri = profiles.firstOrNull { it.canStream() }?.let {
    device.getStreamURI(it, addCredentials = true)
}
val snapshotUri = profiles.firstOrNull { it.canSnapshot() }?.let { 
    device.getSnapshotURI(it)
}
```

## References

http://www.onvif.org/ver10/device/wsdl/devicemgmt.wsdl