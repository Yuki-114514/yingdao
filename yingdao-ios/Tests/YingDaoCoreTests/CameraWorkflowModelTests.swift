import XCTest
@testable import YingDaoCore

@MainActor
final class CameraWorkflowModelTests: XCTestCase {
    func testStoreCapturePrependsNewCaptureAndSelectsIt() {
        let workflow = CameraWorkflowModel()
        workflow.selectGuide(id: "study-detail")

        let first = workflow.storeCapture(
            imageData: Data(repeating: 0x01, count: 1_024),
            createdAt: Date(timeIntervalSince1970: 100)
        )
        let second = workflow.storeCapture(
            imageData: Data(repeating: 0x02, count: 2_048),
            createdAt: Date(timeIntervalSince1970: 200)
        )

        XCTAssertEqual(workflow.captures.count, 2)
        XCTAssertEqual(workflow.captures.first?.id, second.id)
        XCTAssertEqual(workflow.selectedCaptureID, second.id)
        XCTAssertEqual(first.guideID, "study-detail")
        XCTAssertEqual(second.guideTitle, workflow.selectedGuide.title)
    }

    func testToggleFavoriteReplacesCaptureWithUpdatedValue() throws {
        let workflow = CameraWorkflowModel()
        let capture = workflow.storeCapture(imageData: Data(repeating: 0xFF, count: 4_096))

        workflow.toggleFavorite(id: capture.id)

        let updated = try XCTUnwrap(workflow.captures.first)
        XCTAssertEqual(updated.id, capture.id)
        XCTAssertTrue(updated.isFavorite)
        XCTAssertEqual(workflow.selectedCaptureID, capture.id)
        XCTAssertFalse(capture.isFavorite)
    }

    func testDeleteCaptureFallsBackToNextAvailableSelection() {
        let workflow = CameraWorkflowModel()
        let older = workflow.storeCapture(
            imageData: Data(repeating: 0x01, count: 1_000),
            createdAt: Date(timeIntervalSince1970: 10)
        )
        let newer = workflow.storeCapture(
            imageData: Data(repeating: 0x02, count: 2_000),
            createdAt: Date(timeIntervalSince1970: 20)
        )

        workflow.deleteCapture(id: newer.id)

        XCTAssertEqual(workflow.captures.count, 1)
        XCTAssertEqual(workflow.selectedCaptureID, older.id)
        XCTAssertEqual(workflow.captures.first?.id, older.id)
    }

    func testSelectGuideIgnoresUnknownIdentifier() {
        let workflow = CameraWorkflowModel()
        let originalGuideID = workflow.selectedGuideID

        workflow.selectGuide(id: "missing-guide")

        XCTAssertEqual(workflow.selectedGuideID, originalGuideID)
    }
}
