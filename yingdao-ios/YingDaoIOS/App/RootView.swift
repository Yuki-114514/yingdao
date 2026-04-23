import SwiftUI

struct RootView: View {
    @Environment(YingDaoAppModel.self) private var appModel

    var body: some View {
        @Bindable var appModel = appModel

        NavigationStack(path: $appModel.path) {
            CameraHomeView()
                .navigationDestination(for: YingDaoRoute.self) { route in
                    switch route {
                    case .result(let captureID):
                        CaptureResultView(captureID: captureID)
                    case .library:
                        PhotoLibraryView()
                    }
                }
        }
    }
}

#Preview {
    RootView()
        .environment(YingDaoAppModel())
        .environment(CameraSessionController())
}
