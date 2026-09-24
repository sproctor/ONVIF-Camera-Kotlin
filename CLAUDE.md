# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A Kotlin Multiplatform library (`com.seanproctor:onvifcamera`) for talking to ONVIF cameras:
device discovery (WS-Discovery), device info, media profiles, and stream/snapshot URIs.
Published to Maven Central. Targets **Android**, **JVM** and **iOS** (`iosArm64`,
`iosSimulatorArm64`).

Source sets: `commonMain` is platform-free (no `java.*`); `jvmCommonMain` is shared by the JVM and
Android (`java.net` discovery socket, `java.security` SHA-1/`SecureRandom`); `iosMain` has the
BSD-socket and CommonCrypto/Security equivalents. Time is stdlib `kotlin.time` (`Instant`,
`Clock`, `Duration`) — no kotlinx-datetime, and no `java.time`, so Android needs no desugaring.
Kotlin/Native compiles iOS klibs on Linux, so `build`, `checkKotlinAbi` and publishing work there,
but iOS tests only run on macOS (they are skipped elsewhere).

The repo has two Gradle modules:
- `:onvifcamera` — the published library.
- `:demo` — a Compose Multiplatform (Android + desktop JVM + iOS) sample app; not published.
  `:androidDemo` is its Android application; `iosDemo/` is its Xcode project (not a Gradle module).

## Commands

```bash
./gradlew build                          # build + test everything
./gradlew :onvifcamera:build             # build the library only
./gradlew :onvifcamera:jvmTest           # run library tests on the JVM target
./gradlew :onvifcamera:allTests          # run tests across all targets
./gradlew :onvifcamera:iosSimulatorArm64Test   # iOS tests in the simulator (macOS only)
./gradlew :onvifcamera:jvmTest --tests "com.seanproctor.onvifcamera.parsers.ParserTest"   # single test class
./gradlew :demo:run                      # run the desktop demo app
./gradlew :demo:installDebug             # install the Android demo on a connected device
./gradlew :onvifcamera:updateLegacyAbi   # regenerate onvifcamera/api/ dumps after a public API change
./gradlew :onvifcamera:conformanceTest   # ONVIF client conformance suite; NOT run by build, run it after large changes
```

Kotlin's built-in ABI validation (`abiValidation()` in `onvifcamera/build.gradle.kts`) checks the
library's public API against the dumps in `onvifcamera/api/jvm/` and `onvifcamera/api/android/`
as part of `build` (`checkKotlinAbi`). Any change to a public signature fails the build until
`updateLegacyAbi` is run and the updated `.api` files are committed; review that diff as the API
change. The Android dump is the one that covers the `OnvifDiscoveryManager(Context, …)` factory.

CI (`.github/workflows/ci.yml`) runs `./gradlew build` on Linux, and `iosSimulatorArm64Test` and
an `xcodebuild` of the iOS demo on macOS, on pushes and PRs to master;
`release.yml` publishes to Maven Central when a GitHub release is created, so the release tag must
point at the commit that carries the bumped `version`. There is no separate lint step beyond what `build` runs.

### Versions / dependencies

Dependency versions live in `gradle/libs.versions.toml`, managed by the **refreshVersions** plugin
(`./gradlew refreshVersions` then `refreshVersionsCatalog`). The library uses `explicitApi()`, so
every public declaration must have an explicit `public`/`internal` visibility modifier or the build
fails. The library version is set in `onvifcamera/build.gradle.kts` (`version = ...`).

## Architecture

### Public model types

`MediaProfile`, `DiscoveredOnvifDevice` and `OnvifDeviceInformation` are plain classes with
hand-written `equals`/`hashCode`/`toString`, not data classes, so a property can be added with a
default value without removing the previous constructor and `copy` signatures from the binary.
Keep it that way when adding fields: add the property with a default, extend the three overrides.

### Request flow (the core abstraction)

ONVIF is SOAP-over-HTTP. The library never uses a SOAP framework — it builds request bodies as raw
XML strings and parses responses with kotlinx-serialization XML.

1. **`OnvifDevice.requestDevice(url, user, pass)`** is the entry point. It first calls `GetServices`
   on the device, then builds a `namespaceMap` of *service namespace → endpoint path*. This map is
   how the library knows which URL to hit for each later request — different ONVIF operations live
   under different service paths (`ver10/device/wsdl` vs `ver20/media/wsdl`), and cameras advertise
   their own paths. Note: only the path from `GetServices` is trusted; the host is always rewritten
   to the address the caller supplied (`fixHost`/`buildUrl`), working around cameras that report
   wrong/internal IPs.
2. `OnvifRequestType` enum maps each operation to its service namespace; `getEndpointForRequest`
   looks the path up in `namespaceMap` (throws `OnvifServiceUnavailable`, carrying the namespace,
   if the camera doesn't offer that service).
3. **`OnvifCommands`** holds the hand-written SOAP request bodies (constants and builder functions).
4. **`OnvifDevice.execute()`** (companion) is the single HTTP chokepoint. Each `OnvifDevice` owns one
   Ktor `HttpClient` (`AutoCloseable`; `requestDevice` accepts a caller-supplied one). Every
   authenticated request carries a WS-Security UsernameToken built by `WsSecurity`, timestamped in
   device time (`GetSystemDateAndTime` at connect gives the clock offset); HTTP Digest, and Basic
   only when a 401 offers Basic and not Digest (`sendAuthenticated`), remain as the reactive fallback for
   devices that authenticate at the HTTP layer. `execute` posts the SOAP
   body, checks every response body for a SOAP fault (`parseOnvifFault`; cameras send faults with
   200 as well as 400/500) and throws it as `OnvifFault`, then maps remaining non-2xx statuses to
   `OnvifUnauthorized`, `OnvifForbidden` or `OnvifInvalidResponse`. All are subclasses of
   `OnvifException` in `Exceptions.kt`, which is abstract with an internal constructor rather than
   sealed so new subclasses are additive; a `NotAuthorized` fault becomes `OnvifUnauthorized`.
5. **`OnvifXmlParser`** parses responses. Every response is an `Envelope<T>` (see `soap/Envelope.kt`)
   wrapping a typed body; `parseSoap<T>()` is the generic decoder. The `soap/` package holds the
   `@Serializable` data classes for each response type. The parser is lenient
   (`ignoreUnknownChildren`, `pedantic = false`) because cameras vary wildly in what they return.

### Discovery

WS-Discovery is UDP multicast (group `239.255.255.250:3702`).

- `OnvifDiscoveryManager` (interface, commonMain) + `OnvifDiscoveryManagerImpl`. The platform factory
  function `OnvifDiscoveryManager(...)` is `expect`-like: JVM and iOS take only a logger; **Android
  requires a `Context`** (to obtain a `WifiManager` for the multicast lock).
- `ProbingSocketListener` (commonMain) implements `SocketListener`: the probe schedule and the
  receive loop, over a blocking `ProbeSocket` (send to the group on every multicast interface,
  receive with a 500 ms timeout, close) that each platform supplies: `JavaProbeSocket`
  (`jvmCommonMain`, `java.net`) and `PosixProbeSocket` (`iosMain`, BSD sockets via
  `platform.posix`/`platform.darwin`). The Android factory wraps `JavaProbeSocket` in the
  WifiManager `MulticastLock`. Datagrams cross into common code as `Datagram(data, senderHost)`.
- `discoverDevices()` returns a **cold** `Flow<List<DiscoveredOnvifDevice>>`: collecting
  it opens the socket, probes, and listens until the collector is cancelled, which closes the socket
  (a read timeout bounds that). The probe is retransmitted on the SOAP-over-UDP 1.1 multicast
  schedule (3 sends, random 50-250 ms gap doubling to a 500 ms cap); there is no knob for it.
  It is a `runningFold` over an immutable map keyed by sender host — dedupes by
  address, keeps discovery order, `distinctUntilChanged` conflates identical retransmission
  replies. Socket I/O and parsing run on `Dispatchers.IO`. Socket errors fail the
  flow; unparseable replies are logged and dropped. `OnvifDiscoveryManagerImplTest` drives the
  pipeline with a scripted `SocketListener`.

### Logging

`OnvifLogger` is an optional interface threaded through every public API as a nullable param. When
provided it's also wired into Ktor's logging plugin. The library has no default logger.

### Tests

Parser tests live in `onvifcamera/src/commonTest` and decode real captured camera XML stored in
`onvifcamera/src/commonTest/resources/*.xml`. Reading those resource files goes through an
`expect/actual` `readResourceFile` (`TestUtil.kt` + per-target `TestUtil.<platform>.kt`; the iOS
one reads the files in place, from the path `build.gradle.kts` passes to the simulator as
`SIMCTL_CHILD_ONVIF_TEST_RESOURCES`). When
adding support for a new camera quirk, add its captured response as a resource and a parser test.
Media operations use Media2 (`tr2`, `ver20/media`) when the camera advertises it and Media1
(`trt`, `ver10/media`) otherwise (`MediaService`); `GetProfiles` on Media2 asks for
`Type=VideoEncoder` only, since the library finds cameras and their streams, not audio devices.
Fixtures: `profiles*_media1.xml`/`lorex_media1.xml` are real Media1 captures; the Media2
`profiles.xml`/`profiles2.xml`/`lorex.xml` and the Media1 `stream_media1.xml`/`snapshot_media1.xml`
are hand-derived from captures of the other service and say so in a comment. Replace derived
fixtures with real captures when one is available.

## Demo app notes

The demo plays RTSP streams with platform-specific players behind an `expect`/`actual`
`StreamPlayer`: **Media3/ExoPlayer** on Android, **bytedeco FFmpeg** on desktop (the desktop build
selects the correct native FFmpeg artifact per host OS in `demo/build.gradle.kts`). Desktop entry
point is `com.seanproctor.onvifdemo.MainKt`.

On **iOS** the `:demo` module builds a static `OnvifDemo` framework; `iosDemo/` is a SwiftUI app
that calls `MainViewController(playerFactory)` and builds the framework from an Xcode build phase
(`embedAndSignAppleFrameworkForXcode`). iOS has no RTSP player, so `StreamPlayer` hosts a
`UIView` from an `RtspPlayerFactory` the Swift side implements with VLCKit (the `vlckit-spm`
Swift package, pinned to 3.6.0). The Compose `ui`/`foundation`/`runtime` artifacts are pinned to
the Compose plugin version because material3 lags it and mixed versions fail to link on iOS.
CI's `ios-demo` job builds the app with `xcodebuild`; nothing else compiles the Swift sources.
