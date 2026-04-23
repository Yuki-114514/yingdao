import Foundation
import YingDaoCore

@MainActor
func runVerification() -> Int32 {
    let model = CameraWorkflowModel()
    let firstCapture = model.storeCapture(
        imageData: Data(repeating: 0x01, count: 8_192),
        createdAt: Date(timeIntervalSince1970: 10),
    )
    let secondCapture = model.storeCapture(
        imageData: Data(repeating: 0x02, count: 12_288),
        createdAt: Date(timeIntervalSince1970: 20),
    )

    guard model.captures.count == 2 else {
        fputs("Verification failed: expected 2 captures.\n", stderr)
        return 1
    }

    guard model.captures.first?.id == secondCapture.id else {
        fputs("Verification failed: newest capture is not first.\n", stderr)
        return 1
    }

    model.selectGuide(id: GuidePreset.defaults[1].id)
    let guidedCapture = model.storeCapture(imageData: Data(repeating: 0x03, count: 9_000))
    guard guidedCapture.guideID == GuidePreset.defaults[1].id else {
        fputs("Verification failed: selected guide did not propagate.\n", stderr)
        return 1
    }

    model.toggleFavorite(id: firstCapture.id)
    guard model.captures.contains(where: { $0.id == firstCapture.id && $0.isFavorite }) else {
        fputs("Verification failed: favorite toggle did not persist.\n", stderr)
        return 1
    }

    print("YingDaoCore verification passed.")
    return 0
}

exit(runVerification())
