package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import kotlinx.coroutines.flow.Flow

public interface OnvifDiscoveryManager {
    /**
     * Discovers ONVIF devices on the local network with WS-Discovery.
     *
     * The flow is cold: nothing happens until it is collected, and every collection is its own
     * discovery run. Collecting opens a socket, sends `1 + retryCount` probes on each
     * multicast-capable interface and listens for replies until the collector is cancelled;
     * cancelling closes the socket and releases the Android multicast lock. There is no stop
     * call and no scope to manage: the collecting coroutine is the lifetime. Socket I/O and
     * response parsing run on [kotlinx.coroutines.Dispatchers.IO] whatever the collector's
     * dispatcher is.
     *
     * The first emission is an empty list. Each later one is every device found so far, in
     * discovery order, keyed by the address the reply came from, so a camera that answers
     * several probes appears once and keeps its position. Equal consecutive states are not
     * re-emitted. The flow does not complete on its own.
     *
     * Replies that cannot be parsed are logged and dropped. Failing to open or read the socket
     * fails the flow with the underlying exception.
     *
     * @param retryCount how many times to retransmit the probe after the first send, on the
     *   SOAP-over-UDP schedule (a random 50–250 ms gap, doubling per repeat, capped at 500 ms).
     *   Cameras miss probes, so a retry or two finds more of them.
     */
    public fun discoverDevices(retryCount: Int = 1): Flow<List<DiscoveredOnvifDevice>>
}
