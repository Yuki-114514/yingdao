import Foundation

public struct CapturedPhoto: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let createdAt: Date
    public let guideID: String
    public let guideTitle: String
    public let imageData: Data
    public let review: CaptureReview
    public var isFavorite: Bool

    public init(
        id: UUID = UUID(),
        createdAt: Date = .now,
        guideID: String,
        guideTitle: String,
        imageData: Data,
        review: CaptureReview,
        isFavorite: Bool = false,
    ) {
        self.id = id
        self.createdAt = createdAt
        self.guideID = guideID
        self.guideTitle = guideTitle
        self.imageData = imageData
        self.review = review
        self.isFavorite = isFavorite
    }

    public var fileSizeKB: Int {
        max(1, imageData.count / 1024)
    }
}
