import UIKit
import OnvifDemo
import VLCKitSPM

/// Plays an RTSP stream with VLCKit: iOS has no RTSP player of its own. The Compose UI hosts
/// `view` and calls `stop()` when the stream screen goes away.
final class VlcRtspPlayer: NSObject, RtspPlayer {
    let view = UIView()
    private let player = VLCMediaPlayer()

    init(url: String) {
        super.init()
        view.backgroundColor = .black
        player.drawable = view
        guard let streamUrl = URL(string: url) else { return }
        let media = VLCMedia(url: streamUrl)
        // RTP over TCP (interleaved in the RTSP connection): every camera supports it, and it
        // is not lost to Wi-Fi drops the way UDP is.
        media.addOption(":rtsp-tcp")
        // A short buffer, for a live view rather than smooth playback.
        media.addOption(":network-caching=300")
        player.media = media
        player.play()
    }

    func stop() {
        player.stop()
    }
}

final class VlcRtspPlayerFactory: NSObject, RtspPlayerFactory {
    func create(url: String) -> any RtspPlayer {
        VlcRtspPlayer(url: url)
    }
}
