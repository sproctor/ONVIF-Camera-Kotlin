package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import kotlinx.coroutines.flow.Flow

public interface OnvifDiscoveryManager {
    /**
     * Discovers ONVIF devices on the local network with WS-Discovery.
     *
     * The flow is cold: nothing happens until it is collected, and every collection is its own
     * discovery run. Collecting opens a socket, probes every multicast-capable interface and
     * listens for replies until the collector is cancelled; cancelling closes the socket and
     * releases the Android multicast lock. There is no stop call and no scope to manage: the
     * collecting coroutine is the lifetime. Socket I/O and response parsing run on
     * [kotlinx.coroutines.Dispatchers.IO] whatever the collector's dispatcher is.
     *
     * Multicast UDP has no acknowledgement, so the probe is retransmitted on the schedule
     * SOAP-over-UDP 1.1 defines for it: three sends in all, the first immediate and the rest
     * after a random 50-250 ms gap that doubles per repeat and caps at 500 ms. Probing is
     * therefore over within about a second, but devices answer on their own schedule and the
     * flow does not complete on its own. Bound the run by cancelling the collector.
     *
     * The first emission is an empty list. Each later one is every device found so far, in
     * discovery order, keyed by the address the reply came from, so a camera that answers
     * several probes appears once and keeps its position. Equal consecutive states are not
     * re-emitted.
     *
     * Replies that cannot be parsed are logged and dropped. Failing to open or read the socket
     * fails the flow with the underlying exception.
     *
     * On Android, an app that targets Android 17 (API 37) or higher must hold
     * `android.permission.ACCESS_LOCAL_NETWORK`, declared in its manifest and granted at runtime,
     * before collecting: Android 17 blocks local-network UDP for such apps, and the flow fails
     * with an `IOException` if the permission is missing.
     */
    public fun discoverDevices(): Flow<List<DiscoveredOnvifDevice>>
}
