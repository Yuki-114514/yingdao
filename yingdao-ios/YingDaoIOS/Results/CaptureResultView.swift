import SwiftUI
import UIKit

struct CaptureResultView: View {
    let captureID: UUID

    @Environment(YingDaoAppModel.self) private var appModel

    var body: some View {
        Group {
            if let capture = appModel.workflow.captures.first(where: { $0.id == captureID }) {
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        resultHeader(capture: capture)
                        imageCard(capture: capture)
                        reviewCard(capture: capture)
                        noteCard(capture: capture)
                        actionCard(capture: capture)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 32)
                }
                .background(resultBackground.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(.hidden, for: .navigationBar)
            } else {
                ContentUnavailableView(
                    "找不到这张照片",
                    systemImage: "exclamationmark.triangle",
                    description: Text("它可能已经被删除，或者当前会话已经重置。")
                )
            }
        }
    }

    private var resultBackground: some View {
        LinearGradient(
            colors: [
                Color(red: 0.98, green: 0.96, blue: 0.95),
                Color(red: 0.97, green: 0.98, blue: 0.99),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private func resultHeader(capture: CapturedPhoto) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("拍后反馈")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundStyle(Color.black.opacity(0.9))
                Text("这一步先判断画面是否能直接保留，再决定补不补拍。")
                    .font(.subheadline)
                    .foregroundStyle(Color.black.opacity(0.56))
            }

            Spacer(minLength: 8)

            VStack(spacing: 4) {
                Text("\(capture.review.score)")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundStyle(Color.black.opacity(0.9))
                Text("评分")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.black.opacity(0.48))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                Color(red: 0.89, green: 0.86, blue: 0.89),
                in: RoundedRectangle(cornerRadius: 24, style: .continuous)
            )
        }
    }

    private func imageCard(capture: CapturedPhoto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if let image = UIImage(data: capture.imageData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(height: 320)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
            }

            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text(capture.guideTitle)
                        .font(.title3.weight(.heavy))
                        .foregroundStyle(Color.black.opacity(0.88))
                    Text(capture.createdAt.formatted(date: .omitted, time: .shortened))
                        .font(.caption)
                        .foregroundStyle(Color.black.opacity(0.5))
                }

                Spacer()

                if capture.isFavorite {
                    Label("已精选", systemImage: "star.fill")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.orange)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.orange.opacity(0.14), in: Capsule())
                }
            }
        }
    }

    private func reviewCard(capture: CapturedPhoto) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("拍后判断")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.black.opacity(0.48))
                    Text(capture.review.label)
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundStyle(Color.black.opacity(0.9))
                }

                Spacer()

                Text("\(capture.review.score)分")
                    .font(.headline.weight(.heavy))
                    .foregroundStyle(Color(red: 0.11, green: 0.45, blue: 0.42))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        Color(red: 0.86, green: 0.93, blue: 0.91),
                        in: Capsule()
                    )
            }

            Text(capture.review.summary)
                .font(.body)
                .foregroundStyle(Color.black.opacity(0.62))

            HStack(spacing: 10) {
                scoreChip(title: "稳定", value: capture.review.stabilityScore)
                scoreChip(title: "主体", value: capture.review.subjectScore)
                scoreChip(title: "构图", value: capture.review.compositionScore)
                scoreChip(title: "情绪", value: capture.review.emotionScore)
            }
        }
        .padding(20)
        .background(
            Color(red: 0.89, green: 0.87, blue: 0.9),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
    }

    private func noteCard(capture: CapturedPhoto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("下一步建议")
                .font(.title3.weight(.heavy))
                .foregroundStyle(Color.black.opacity(0.9))

            Text(capture.review.keepReason.isEmpty ? capture.review.retakeReason : capture.review.keepReason)
                .font(.body)
                .foregroundStyle(Color.black.opacity(0.72))

            Text("下一步：\(capture.review.nextAction)")
                .font(.body.weight(.semibold))
                .foregroundStyle(Color(red: 0.11, green: 0.45, blue: 0.42))

            ForEach(capture.review.notes, id: \.self) { note in
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(Color(red: 0.11, green: 0.45, blue: 0.42))
                    Text(note)
                        .font(.body)
                        .foregroundStyle(Color.black.opacity(0.72))
                }
            }
        }
        .padding(20)
        .background(
            Color.white.opacity(0.82),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
    }

    private func actionCard(capture: CapturedPhoto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("下一步")
                .font(.title3.weight(.heavy))
                .foregroundStyle(Color.black.opacity(0.9))

            HStack(spacing: 12) {
                actionButton(title: "继续拍摄", fill: Color(red: 0.11, green: 0.45, blue: 0.42), foreground: .white) {
                    appModel.returnToCamera()
                }

                actionButton(
                    title: capture.isFavorite ? "取消收藏" : "加入精选",
                    fill: Color.black.opacity(0.06),
                    foreground: Color.black.opacity(0.84)
                ) {
                    appModel.workflow.toggleFavorite(id: capture.id)
                }
            }

            actionButton(
                title: "打开照片列表",
                fill: Color(red: 0.87, green: 0.92, blue: 1.0),
                foreground: Color(red: 0.27, green: 0.52, blue: 0.97)
            ) {
                appModel.openLibrary()
            }
        }
        .padding(20)
        .background(
            Color.white.opacity(0.82),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
    }

    private func scoreChip(title: String, value: Int) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.black.opacity(0.48))
            Text("\(value)")
                .font(.headline.weight(.heavy))
                .foregroundStyle(Color.black.opacity(0.88))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func actionButton(
        title: String,
        fill: Color,
        foreground: Color,
        action: @escaping () -> Void,
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.headline.weight(.bold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .foregroundStyle(foreground)
                .background(fill, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    let model = CameraWorkflowModel()
    let capture = model.storeCapture(imageData: Data(repeating: 0xAB, count: 12_000))
    return CaptureResultView(captureID: capture.id)
        .environment(YingDaoAppModel(workflow: model))
        .environment(CameraSessionController())
}
