import Foundation

public struct CaptureReview: Equatable, Sendable {
    public let score: Int
    public let label: String
    public let summary: String
    public let notes: [String]
    public let stabilityScore: Int
    public let subjectScore: Int
    public let compositionScore: Int
    public let emotionScore: Int
    public let keepReason: String
    public let retakeReason: String
    public let nextAction: String

    public init(
        score: Int,
        label: String,
        summary: String,
        notes: [String],
        stabilityScore: Int,
        subjectScore: Int,
        compositionScore: Int,
        emotionScore: Int,
        keepReason: String,
        retakeReason: String,
        nextAction: String,
    ) {
        self.score = score
        self.label = label
        self.summary = summary
        self.notes = notes
        self.stabilityScore = stabilityScore
        self.subjectScore = subjectScore
        self.compositionScore = compositionScore
        self.emotionScore = emotionScore
        self.keepReason = keepReason
        self.retakeReason = retakeReason
        self.nextAction = nextAction
    }
}
