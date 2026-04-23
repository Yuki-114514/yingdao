import SwiftUI

@main
struct YingDaoIOSApp: App {
    @State private var appModel = YingDaoAppModel()
    @State private var cameraController = CameraSessionController()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appModel)
                .environment(cameraController)
        }
    }
}
