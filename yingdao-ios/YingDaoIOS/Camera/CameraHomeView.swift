import SwiftUI
import UIKit

struct CameraHomeView: View {
    @Environment(YingDaoAppModel.self) private var appModel
    @Environment(CameraSessionController.self) private var cameraController
    @Environment(\.openURL) private var openURL

    @State private var isCaptureTaskRunning = false

    var body: some View {
        @Bindable var workflow = appModel.workflow

        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                heroHeader(captureCount: workflow.captures.count)

                HStack(spacing: 10) {
                    metricChip(label: "已拍照片", value: "\(workflow.captures.count)")
                    metricChip(label: "当前镜头", value: workflow.selectedGuide.title)
                }

                LiveGuideOverlay(
                    guide: workflow.selectedGuide,
                    captureCount: workflow.captures.count
                )

                previewCard

                captureControls

                SectionHeader(
                    title: "镜头总览",
                    subtitle: "像安卓端一样，先把关键校园镜头按顺序拍齐。",
                )

                guideSelector(workflow: workflow)
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 36)
        }
        .background(appBackground.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            await cameraController.prepareSession()
        }
        .alert(
            "相机不可用",
            isPresented: Binding(
                get: { cameraController.lastErrorMessage != nil },
                set: { newValue in
                    if !newValue {
                        cameraController.lastErrorMessage = nil
                    }
                }
            ),
            actions: {
                if case .denied = cameraController.status {
                    Button("去设置") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            openURL(url)
                        }
                    }
                }
                Button("知道了", role: .cancel) {
                    cameraController.lastErrorMessage = nil
                }
            },
            message: {
                Text(cameraController.lastErrorMessage ?? "未知错误")
            }
        )
    }

    private var appBackground: some View {
        LinearGradient(
            colors: [
                Color(red: 0.98, green: 0.95, blue: 0.96),
                Color(red: 0.95, green: 0.97, blue: 0.99),
                Color.white,
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    @ViewBuilder
    private func heroHeader(captureCount: Int) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("拍摄执行")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundStyle(Color.black.opacity(0.92))
                Text("先看镜头为什么要拍，再拍，再马上得到能不能留的解释型反馈。")
                    .font(.subheadline)
                    .foregroundStyle(Color.black.opacity(0.58))
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 12)

            Button {
                appModel.openLibrary()
            } label: {
                VStack(spacing: 6) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.headline.weight(.bold))
                    Text("\(captureCount)")
                        .font(.title3.weight(.heavy))
                }
                .foregroundStyle(Color.black.opacity(0.85))
                .frame(width: 72, height: 88)
                .background(
                    Color.white.opacity(0.78),
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous)
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func metricChip(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.black.opacity(0.52))
            Text(value)
                .font(.title3.weight(.heavy))
                .foregroundStyle(Color.black.opacity(0.9))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            Color(red: 0.88, green: 0.86, blue: 0.89),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
    }

    private var previewCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                cameraSurface
                    .frame(height: 290)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))

                previewStatusCard
                    .padding(14)
            }
        }
    }

    private var cameraSurface: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(Color(red: 0.91, green: 0.9, blue: 0.92))

            CameraPreviewView(session: cameraController.previewSession)

            if case .denied = cameraController.status {
                unavailableState(
                    title: "还没有相机权限",
                    subtitle: "允许相机权限后，才能在 iPhone 上真实预览和拍照。"
                )
            } else if case .failed(let message) = cameraController.status {
                unavailableState(
                    title: "相机暂时不可用",
                    subtitle: message
                )
            } else if cameraController.status != .ready {
                unavailableState(
                    title: "正在准备相机",
                    subtitle: "第一次进入时会初始化相机，会稍微等一下。"
                )
            }
        }
    }

    private var previewStatusCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(previewStatusTitle)
                .font(.headline.weight(.bold))
                .foregroundStyle(Color.black.opacity(0.88))
            Text(previewStatusMessage)
                .font(.caption)
                .foregroundStyle(Color.black.opacity(0.6))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            Color.white.opacity(0.9),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
        .frame(maxWidth: 260, alignment: .leading)
    }

    private var previewStatusTitle: String {
        switch cameraController.status {
        case .ready:
            if isCaptureTaskRunning {
                return "拍摄中"
            }
            return "准备拍摄"
        case .requestingPermission:
            return "请求权限中"
        case .configuring:
            return "相机准备中"
        case .denied:
            return "等待权限"
        case .failed:
            return "相机异常"
        case .idle:
            return "等待相机"
        }
    }

    private var previewStatusMessage: String {
        if isCaptureTaskRunning {
            return "拍完这张会直接进入拍后结果页。"
        }

        switch cameraController.status {
        case .ready:
            return "当前镜头就绪，轻点下方快门开始拍摄。"
        case .requestingPermission:
            return "允许相机访问后才能继续预览。"
        case .configuring:
            return "正在初始化相机和取景预览。"
        case .denied:
            return "去设置里打开相机权限后再试。"
        case .failed(let message):
            return message
        case .idle:
            return "准备好之后会显示实时预览。"
        }
    }

    private var captureControls: some View {
        HStack(alignment: .center, spacing: 16) {
            Button {
                appModel.openLibrary()
            } label: {
                Image(systemName: "square.stack")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color(red: 0.11, green: 0.45, blue: 0.42))
                    .frame(width: 64, height: 64)
                    .background(
                        Color(red: 0.86, green: 0.93, blue: 0.91),
                        in: Circle()
                    )
            }
            .buttonStyle(.plain)

            Button {
                captureCurrentFrame()
            } label: {
                ZStack {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 96, height: 96)

                    Circle()
                        .stroke(Color(red: 0.11, green: 0.45, blue: 0.42), lineWidth: 5)
                        .frame(width: 80, height: 80)

                    if isCaptureTaskRunning {
                        ProgressView()
                            .tint(Color(red: 0.11, green: 0.45, blue: 0.42))
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(!cameraController.canCapture || isCaptureTaskRunning)

            VStack(alignment: .leading, spacing: 6) {
                Text(cameraController.canCapture ? "轻点快门" : "等待相机")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(Color.black.opacity(0.86))
                Text("拍完直接进入拍后反馈。")
                    .font(.caption)
                    .foregroundStyle(Color.black.opacity(0.56))
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 18)
        .background(
            Color.white.opacity(0.72),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
    }

    private func guideSelector(workflow: CameraWorkflowModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(workflow.guides) { guide in
                Button {
                    workflow.selectGuide(id: guide.id)
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(guide.title)
                                .font(.title3.weight(.heavy))
                                .foregroundStyle(Color.black.opacity(0.9))
                            Text(guide.subtitle)
                                .font(.body)
                                .foregroundStyle(Color.black.opacity(0.58))
                            Text(guide.overlayHint)
                                .font(.caption)
                                .foregroundStyle(Color.black.opacity(0.46))
                        }

                        Spacer(minLength: 8)

                        Text(workflow.selectedGuideID == guide.id ? "待拍摄" : "可切换")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(
                                workflow.selectedGuideID == guide.id
                                    ? Color(red: 0.27, green: 0.52, blue: 0.97)
                                    : Color.black.opacity(0.46)
                            )
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                workflow.selectedGuideID == guide.id
                                    ? Color(red: 0.87, green: 0.92, blue: 1.0)
                                    : Color.black.opacity(0.06),
                                in: Capsule()
                            )
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        workflow.selectedGuideID == guide.id
                            ? Color(red: 0.9, green: 0.88, blue: 0.9)
                            : Color.white.opacity(0.76),
                        in: RoundedRectangle(cornerRadius: 28, style: .continuous)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func captureCurrentFrame() {
        guard !isCaptureTaskRunning else { return }

        Task {
            isCaptureTaskRunning = true
            defer { isCaptureTaskRunning = false }

            do {
                let imageData = try await cameraController.capturePhoto()
                let capture = appModel.workflow.storeCapture(imageData: imageData)
                appModel.openResult(for: capture.id)
            } catch {
                cameraController.lastErrorMessage = error.localizedDescription
            }
        }
    }

    @ViewBuilder
    private func unavailableState(title: String, subtitle: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "camera.aperture")
                .font(.system(size: 38, weight: .semibold))
                .foregroundStyle(Color.black.opacity(0.72))
            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(Color.black.opacity(0.82))
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(Color.black.opacity(0.56))
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .background(
            Color.white.opacity(0.88),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
        .padding(.horizontal, 24)
    }
}

private struct SectionHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 22, weight: .black, design: .rounded))
                .foregroundStyle(Color.black.opacity(0.9))
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(Color.black.opacity(0.54))
        }
    }
}

#Preview {
    CameraHomeView()
        .environment(YingDaoAppModel())
        .environment(CameraSessionController())
}
