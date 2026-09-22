package com.seanproctor.onvifcamera.network

import kotlinx.coroutines.flow.Flow
import java.net.DatagramPacket

/** Sends WS-Discovery probes and yields the datagrams that come back. */
internal interface SocketListener {

    /**
     * A cold flow. Collecting it opens a socket, sends `1 + retryCount` probes and emits every
     * datagram received until the collector is cancelled, then closes the socket. Blocking
     * socket work runs on [kotlinx.coroutines.Dispatchers.IO]. Socket failures fail the flow.
     *
     * @param retryCount how many additional probes to send after the first
     */
    fun listenForPackets(retryCount: Int): Flow<DatagramPacket>
}
