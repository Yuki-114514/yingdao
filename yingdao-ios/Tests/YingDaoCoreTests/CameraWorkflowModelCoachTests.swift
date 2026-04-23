import Testing
import Foundation
@testable import YingDaoCore

@Suite("Camera workflow coaching")
struct CameraWorkflowModelCoachTests {
    @MainActor
    @Test("default guides expose coaching metadata")
    func defaultGuidesExposeCoachingMetadata() {
        let guide = GuidePreset.defaults[0]

        #expect(!guide.whyThisShotMatters.isEmpty)
        #expect(!guide.successChecklist.isEmpty)
        #expect(!guide.difficultyHint.isEmpty)
        #expect(!guide.storyRole.displayTitle.isEmpty)
    }

    @MainActor
    @Test("stored capture review exposes explainable dimensions")
    func storedCaptureReviewExposesExplainableDimensions() {
        let workflow = CameraWorkflowModel()
        let capture = workflow.storeCapture(imageData: Data(repeating: 0x01, count: 8_192))

        #expect(capture.review.stabilityScore > 0)
        #expect(capture.review.subjectScore > 0)
        #expect(capture.review.compositionScore > 0)
        #expect(capture.review.emotionScore > 0)
        #expect(!capture.review.nextAction.isEmpty)
        #expect(capture.review.keepReason.isEmpty == false || capture.review.retakeReason.isEmpty == false)
    }

    @MainActor
    @Test("selected guide metadata flows into capture review notes")
    func selectedGuideMetadataFlowsIntoCaptureReviewNotes() {
        let workflow = CameraWorkflowModel()
        workflow.selectGuide(id: GuidePreset.defaults[1].id)

        let capture = workflow.storeCapture(imageData: Data(repeating: 0x02, count: 4_096))

        #expect(capture.review.notes.contains(where: { $0.contains(workflow.selectedGuide.whyThisShotMatters) }))
        #expect(capture.review.notes.contains(where: { $0.contains(workflow.selectedGuide.successChecklist[0]) }))
    }

    @MainActor
    @Test("high priority guides default to retake guidance")
    func highPriorityGuidesDefaultToRetakeGuidance() {
        let workflow = CameraWorkflowModel()
        workflow.selectGuide(id: GuidePreset.defaults[0].id)

        let capture = workflow.storeCapture(imageData: Data(repeating: 0x03, count: 4_096))

        #expect(capture.review.label == "建议补拍")
        #expect(capture.review.keepReason.isEmpty)
        #expect(!capture.review.retakeReason.isEmpty)
        #expect(capture.review.nextAction.contains(workflow.selectedGuide.successChecklist[0]))
    }

    @MainActor
    @Test("low priority guides can produce keep guidance")
    func lowPriorityGuidesCanProduceKeepGuidance() {
        let customGuide = GuidePreset(
            id: "library-detail",
            title: "图书馆细节",
            subtitle: "记录一个安静又有质感的学习瞬间",
            framingTip: "靠近书页和手部动作，控制背景干扰。",
            actionTip: "在翻页或写字的一瞬间按下快门。",
            overlayHint: "先拍一张最稳的版本。",
            storyRole: .moment,
            whyThisShotMatters: "补足学习状态的细节证据。",
            successChecklist: ["主体清楚", "动作完整"],
            difficultyHint: "不要为了近景牺牲稳定。",
            retakePriority: .low,
        )
        let workflow = CameraWorkflowModel(guides: [customGuide])

        let capture = workflow.storeCapture(imageData: Data(repeating: 0x04, count: 4_100))

        #expect(capture.review.label == "可直接保留")
        #expect(!capture.review.keepReason.isEmpty)
        #expect(capture.review.retakeReason.isEmpty)
        #expect(capture.review.nextAction.contains("继续推进下一个镜头"))
    }

    @MainActor
    @Test("relationship guides get higher emotion score than non relationship guides")
    func relationshipGuidesGetHigherEmotionScoreThanNonRelationshipGuides() {
        let relationshipGuide = GuidePreset.defaults[2]
        let nonRelationshipGuide = GuidePreset.defaults[1]
        let relationshipWorkflow = CameraWorkflowModel(guides: [relationshipGuide])
        let nonRelationshipWorkflow = CameraWorkflowModel(guides: [nonRelationshipGuide])
        let imageData = Data(repeating: 0x05, count: 4_101)

        let relationshipCapture = relationshipWorkflow.storeCapture(imageData: imageData)
        let nonRelationshipCapture = nonRelationshipWorkflow.storeCapture(imageData: imageData)

        #expect(relationshipCapture.review.emotionScore > nonRelationshipCapture.review.emotionScore)
    }
}
