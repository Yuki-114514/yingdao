import SwiftUI

struct PhotoLibraryView: View {
    @Environment(YingDaoAppModel.self) private var appModel

    private let columns = [
        GridItem(.adaptive(minimum: 160), spacing: 16),
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                libraryHeader

                if appModel.workflow.captures.isEmpty {
                    emptyState
                } else {
                    summaryCard

                    Text("照片清单")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundStyle(Color.black.opacity(0.9))

                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(appModel.workflow.captures) { capture in
                            Button {
                                appModel.openResult(for: capture.id)
                            } label: {
                                PhotoThumbnailCard(capture: capture)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button(capture.isFavorite ? "取消精选" : "加入精选") {
                                    appModel.workflow.toggleFavorite(id: capture.id)
                                }
                                Button("删除", role: .destructive) {
                                    appModel.workflow.deleteCapture(id: capture.id)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 32)
        }
        .background(libraryBackground.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var libraryBackground: some View {
        LinearGradient(
            colors: [
                Color(red: 0.98, green: 0.96, blue: 0.95),
                Color(red: 0.96, green: 0.98, blue: 0.99),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var libraryHeader: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("照片列表")
                .font(.system(size: 34, weight: .black, design: .rounded))
                .foregroundStyle(Color.black.opacity(0.9))
            Text("先看哪些镜头已经拍到，再决定哪些留作精选、哪些需要重拍。")
                .font(.subheadline)
                .foregroundStyle(Color.black.opacity(0.56))
        }
    }

    private var summaryCard: some View {
        HStack(spacing: 12) {
            summaryMetric(label: "已拍", value: "\(appModel.workflow.captures.count)")
            summaryMetric(
                label: "精选",
                value: "\(appModel.workflow.captures.filter(\.isFavorite).count)"
            )
        }
    }

    private func summaryMetric(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.black.opacity(0.48))
            Text(value)
                .font(.title2.weight(.heavy))
                .foregroundStyle(Color.black.opacity(0.9))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            Color(red: 0.89, green: 0.86, blue: 0.89),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("还没有拍到照片")
                .font(.title3.weight(.heavy))
                .foregroundStyle(Color.black.opacity(0.9))
            Text("先在相机首页拍一张，再回来这里看列表和精选。")
                .font(.body)
                .foregroundStyle(Color.black.opacity(0.58))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
        .background(
            Color.white.opacity(0.82),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
    }
}

#Preview {
    let workflow = CameraWorkflowModel()
    _ = workflow.storeCapture(imageData: Data(repeating: 0xFF, count: 15_000))
    _ = workflow.storeCapture(imageData: Data(repeating: 0xA0, count: 10_000))
    return PhotoLibraryView()
        .environment(YingDaoAppModel(workflow: workflow))
        .environment(CameraSessionController())
}
