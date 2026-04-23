import SwiftUI
import UIKit

struct PhotoThumbnailCard: View {
    let capture: CapturedPhoto

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let image = UIImage(data: capture.imageData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(height: 180)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            } else {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.secondary.opacity(0.14))
                    .frame(height: 180)
                    .overlay {
                        Image(systemName: "photo")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                    }
            }

            Text(capture.guideTitle)
                .font(.headline)
                .lineLimit(1)
                .foregroundStyle(Color.black.opacity(0.88))

            HStack {
                Text("\(capture.review.score) 分")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .foregroundStyle(Color(red: 0.11, green: 0.45, blue: 0.42))
                    .background(
                        Color(red: 0.86, green: 0.93, blue: 0.91),
                        in: Capsule()
                    )

                if capture.isFavorite {
                    Image(systemName: "star.fill")
                        .foregroundStyle(.yellow)
                }

                Spacer()

                Text(capture.createdAt.formatted(date: .omitted, time: .shortened))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(14)
        .background(
            Color.white.opacity(0.82),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
    }
}
