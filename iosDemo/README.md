# iOS demo

The iOS app for the demo: a SwiftUI shell around the shared Compose UI in `:demo`, which it links
as the `OnvifDemo` framework. It needs a Mac with Xcode and a JDK (17 or newer) for Gradle.

Open `iosDemo.xcodeproj` and run the `iosDemo` scheme. The first build runs
`./gradlew :demo:embedAndSignAppleFrameworkForXcode` from the "Compile Kotlin Framework" phase,
which takes a while; Xcode also fetches VLCKit.

- **Simulator:** nothing to set up.
- **Device:** put your team in `Configuration/Config.xcconfig` (`TEAM_ID=...`), and change
  `BUNDLE_ID` if `com.seanproctor.onvifdemo` is taken for your team.

## Streams

iOS has no RTSP player (AVPlayer cannot play RTSP), so the stream screen uses VLCKit, from the
[vlckit-spm](https://github.com/tylerjonesio/vlckit-spm) Swift package (MobileVLCKit 3.6.0,
LGPL). `VlcRtspPlayer.swift` implements the Kotlin `RtspPlayerFactory`, and the Compose
`StreamPlayer` hosts its view.

## Discovery

In the simulator, Scan works as it is: the simulator runs on the Mac's network stack and does not
enforce iOS's multicast restriction.

On a device, sending the WS-Discovery multicast probe needs Apple's
`com.apple.developer.networking.multicast` entitlement, which Apple grants per team on request
([request form](https://developer.apple.com/contact/request/networking-multicast)). The project
does not ask for it, because signing fails for a team that has not been granted it. Without it,
Scan reports that discovery failed; connecting by address works. Once your team has the
entitlement, add the "Multicast Networking" capability to the target.

The first local-network access (a scan or a connection) shows iOS's local network prompt;
`NSLocalNetworkUsageDescription` in `Info.plist` is its text.
