# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A Kotlin Multiplatform library (`com.seanproctor:onvifcamera`) for talking to ONVIF cameras:
device discovery (WS-Discovery), device info, media profiles, and stream/snapshot URIs.
Published to Maven Central. Targets **Android** and **JVM** (iOS is stubbed out in the build
config but not yet implemented — it requires reworking the socket interfaces, which currently
depend on `java.net`).

The repo has two Gradle modules:
- `:onvifcamera` — the published library.
- `:demo` — a Compose Multiplatform (Android + desktop JVM) sample app; not published.

## Commands

```bash
./gradlew build                          # build + test everything
./gradlew :onvifcamera:build             # build the library only
./gradlew :onvifcamera:jvmTest           # run library tests on the JVM target
./gradlew :onvifcamera:allTests          # run tests across all targets
./gradlew :onvifcamera:jvmTest --tests "com.seanproctor.onvifcamera.parsers.ParserTest"   # single test class
./gradlew :demo:run                      # run the desktop demo app
./gradlew :demo:installDebug             # install the Android demo on a connected device
```

CI (`.github/workflows/build.yml`) runs `./gradlew build` and publishes to Maven Central only on
GitHub release creation. There is no separate lint step beyond what `build` runs.

### Versions / dependencies

Dependency versions live in `gradle/libs.versions.toml`, managed by the **refreshVersions** plugin
(`./gradlew refreshVersions` then `refreshVersionsCatalog`). The library uses `explicitApi()`, so
every public declaration must have an explicit `public`/`internal` visibility modifier or the build
fails. The library version is set in `onvifcamera/build.gradle.kts` (`version = ...`).

## Architecture

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
   looks the path up in `namespaceMap` (throws `OnvifServiceUnavailable` if the camera doesn't
   offer that service).
3. **`OnvifCommands`** holds the hand-written SOAP request bodies (constants and builder functions).
4. **`OnvifDevice.execute()`** (companion) is the single HTTP chokepoint. It spins up a fresh Ktor
   `HttpClient` per call, installs Basic + Digest auth when credentials are present, posts the SOAP
   body, and maps non-2xx statuses to typed exceptions (`OnvifUnauthorized`, `OnvifForbidden`,
   `OnvifInvalidResponse` — all in `Exceptions.kt`).
5. **`OnvifXmlParser`** parses responses. Every response is an `Envelope<T>` (see `soap/Envelope.kt`)
   wrapping a typed body; `parseSoap<T>()` is the generic decoder. The `soap/` package holds the
   `@Serializable` data classes for each response type. The parser is lenient
   (`ignoreUnknownChildren`, `pedantic = false`) because cameras vary wildly in what they return.

### Discovery

WS-Discovery is UDP multicast and is **JVM-only code even in `commonMain`** — the discovery classes
import `java.net.*` directly, which is why iOS isn't supported yet.

- `OnvifDiscoveryManager` (interface, commonMain) + `OnvifDiscoveryManagerImpl`. The platform factory
  function `OnvifDiscoveryManager(...)` is `expect`-like: JVM takes only a logger; **Android requires
  a `Context`** (to obtain a `WifiManager` for the multicast lock).
- `SocketListener` / `BaseSocketListener` do the multicast work (group `239.255.255.250:3702`).
  The Android vs JVM subclasses differ only in `acquireMulticastLock`/`releaseMulticastLock`
  (`AndroidSocketListener` holds a WifiManager `MulticastLock`; the JVM one is a no-op).
- `discoverDevices()` returns a `Flow<List<DiscoveredOnvifDevice>>` backed by a `MutableStateFlow`
  of a `persistentHashMap` keyed by source `InetAddress`, so the list grows as probe responses
  arrive and dedupes by address.

### Logging

`OnvifLogger` is an optional interface threaded through every public API as a nullable param. When
provided it's also wired into Ktor's logging plugin. The library has no default logger.

### Tests

Parser tests live in `onvifcamera/src/commonTest` and decode real captured camera XML stored in
`onvifcamera/src/commonTest/resources/*.xml`. Reading those resource files goes through an
`expect/actual` `readResourceFile` (`TestUtil.kt` + per-target `TestUtil.<platform>.kt`). When
adding support for a new camera quirk, add its captured response as a resource and a parser test.

## Demo app notes

The demo plays RTSP streams with platform-specific players behind an `expect`/`actual`
`StreamPlayer`: **Media3/ExoPlayer** on Android, **bytedeco FFmpeg** on desktop (the desktop build
selects the correct native FFmpeg artifact per host OS in `demo/build.gradle.kts`). Desktop entry
point is `com.seanproctor.onvifdemo.MainKt`.
