# ONVIFCameraAndroid
Example on how to connect to an ONVIF camera on Android, and dependency to ease the development of an ONVIF Android app.

![ONVIF Camera Android](images/demo.png)


Install with Gradle (must have mavenCentral in repositories):

```groovy
implementation 'com.seanproctor:onvifcamera:1.3.0'
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
