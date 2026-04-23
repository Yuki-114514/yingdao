import Foundation
import Observation

@MainActor
@Observable
public final class CameraWorkflowModel {
    public private(set) var guides: [GuidePreset]
    public var selectedGuideID: String
    public private(set) var captures: [CapturedPhoto]
    public var selectedCaptureID: UUID?

    public init(
        guides: [GuidePreset] = GuidePreset.defaults,
        captures: [CapturedPhoto] = [],
    ) {
        let resolvedGuides = guides.isEmpty ? GuidePreset.defaults : guides
        self.guides = resolvedGuides
        self.selectedGuideID = resolvedGuides[0].id
        self.captures = captures
        self.selectedCaptureID = captures.first?.id
    }

    public var selectedGuide: GuidePreset {
        guides.first(where: { $0.id == selectedGuideID }) ?? guides[0]
    }

    public var selectedCapture: CapturedPhoto? {
        guard let selectedCaptureID else { return captures.first }
        return captures.first(where: { $0.id == selectedCaptureID })
    }

    @discardableResult
    public func storeCapture(
        imageData: Data,
        createdAt: Date = .now,
    ) -> CapturedPhoto {
        let guide = selectedGuide
        let capture = CapturedPhoto(
            createdAt: createdAt,
            guideID: guide.id,
            guideTitle: guide.title,
            imageData: imageData,
            review: buildReview(for: imageData, guide: guide),
        )
        captures.insert(capture, at: 0)
        selectedCaptureID = capture.id
        return capture
    }

    public func selectGuide(id: String) {
        guard guides.contains(where: { $0.id == id }) else { return }
        selectedGuideID = id
    }

    public func selectCapture(id: UUID) {
        guard captures.contains(where: { $0.id == id }) else { return }
        selectedCaptureID = id
    }

    public func toggleFavorite(id: UUID) {
        guard let index = captures.firstIndex(where: { $0.id == id }) else { return }
        let updated = CapturedPhoto(
            id: captures[index].id,
            createdAt: captures[index].createdAt,
            guideID: captures[index].guideID,
            guideTitle: captures[index].guideTitle,
            imageData: captures[index].imageData,
            review: captures[index].review,
            isFavorite: !captures[index].isFavorite,
        )
        captures[index] = updated
        selectedCaptureID = updated.id
    }

    public func deleteCapture(id: UUID) {
        captures.removeAll(where: { $0.id == id })
        if selectedCaptureID == id {
            selectedCaptureID = captures.first?.id
        }
    }

    public func clearSelection() {
        selectedCaptureID = nil
    }

    private func buildReview(
        for imageData: Data,
        guide: GuidePreset,
    ) -> CaptureReview {
        let baseScore: Int = switch guide.retakePriority {
        case .high: 72
        case .medium: 78
        case .low: 84
        }
        let stabilityScore = min(94, max(60, baseScore + Int(imageData.count % 5) - 2))
        let subjectScore = min(96, max(62, baseScore + Int(imageData.count % 7)))
        let compositionScore = min(95, max(61, baseScore + Int(imageData.count % 6) - 1))
        let emotionBonus = guide.storyRole == .relationship ? 4 : 1
        let emotionScore = min(96, max(60, baseScore + emotionBonus + Int(imageData.count % 4) - 1))
        let score = Int(Double(stabilityScore + subjectScore + compositionScore + emotionScore) / 4.0)
        let isKeepable = score >= 80
        let label = isKeepable ? "可直接保留" : "建议补拍"
        let summary = if isKeepable {
            "这一张已经完成当前镜头目标，可以先进入精选列表。"
        } else {
            "这张已经接近可用，但还差一个关键点，建议再补一张更稳的版本。"
        }
        let keepReason = if isKeepable {
            "保留它是因为它完成了“\(guide.whyThisShotMatters)”。"
        } else {
            ""
        }
        let retakeReason = if isKeepable {
            ""
        } else {
            "当前最影响保留的是画面稳定或主体明确度，而这条镜头承担的是“\(guide.storyRole.displayTitle)”。"
        }
        let nextAction = if isKeepable {
            guide.retakePriority == .high ? "这张先保留，再补一张更稳版本会更保险。" : "这张可以先通过，继续推进下一个镜头。"
        } else {
            "优先补到：\(guide.successChecklist.first ?? guide.overlayHint)"
        }

        return CaptureReview(
            score: score,
            label: label,
            summary: summary,
            notes: [
                "这条为什么要拍：\(guide.whyThisShotMatters)",
                "过关标准：\(guide.successChecklist.joined(separator: " / "))",
                "构图建议：\(guide.framingTip)",
                "动作建议：\(guide.actionTip)",
            ],
            stabilityScore: stabilityScore,
            subjectScore: subjectScore,
            compositionScore: compositionScore,
            emotionScore: emotionScore,
            keepReason: keepReason,
            retakeReason: retakeReason,
            nextAction: nextAction,
        )
    }
}
