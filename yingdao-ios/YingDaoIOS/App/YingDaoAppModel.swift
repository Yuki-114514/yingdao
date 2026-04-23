import Foundation
import Observation

@MainActor
@Observable
final class YingDaoAppModel {
    var path: [YingDaoRoute] = []
    let workflow: CameraWorkflowModel

    init(workflow: CameraWorkflowModel = CameraWorkflowModel()) {
        self.workflow = workflow
    }

    func openResult(for captureID: UUID) {
        path.append(.result(captureID))
    }

    func openLibrary() {
        path.append(.library)
    }

    func returnToCamera() {
        path.removeAll()
    }
}
