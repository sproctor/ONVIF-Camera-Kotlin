import SwiftUI
import OnvifDemo

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.keyboard)
        }
    }
}

/// Hosts the shared Compose UI from the demo module's OnvifDemo framework.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(playerFactory: VlcRtspPlayerFactory())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
