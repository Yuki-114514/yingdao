import SwiftUI

struct LiveGuideOverlay: View {
    let guide: GuidePreset
    let captureCount: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("当前镜头")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.black.opacity(0.46))
                    Text(guide.title)
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundStyle(Color.black.opacity(0.9))
                    Text(guide.subtitle)
                        .font(.body)
                        .foregroundStyle(Color.black.opacity(0.58))
                }

                Spacer(minLength: 8)

                Text("已拍 \(captureCount)")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Color(red: 0.27, green: 0.52, blue: 0.97))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        Color(red: 0.88, green: 0.93, blue: 1.0),
                        in: Capsule()
                    )
            }

            Divider()
                .overlay(Color.black.opacity(0.08))

            VStack(spacing: 12) {
                overlayRow(title: "为什么拍", value: guide.whyThisShotMatters)
                overlayRow(title: "过关标准", value: guide.successChecklist.joined(separator: " / "))
                overlayRow(title: "难点", value: guide.difficultyHint)
                overlayRow(title: "构图", value: guide.framingTip)
                overlayRow(title: "动作", value: guide.actionTip)
                overlayRow(title: "提示", value: guide.overlayHint)
            }
        }
        .padding(20)
        .background(
            Color(red: 0.88, green: 0.86, blue: 0.89),
            in: RoundedRectangle(cornerRadius: 30, style: .continuous)
        )
    }

    private func overlayRow(title: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(title)
                .font(.body.weight(.bold))
                .foregroundStyle(Color.black.opacity(0.48))
                .frame(width: 52, alignment: .leading)
            Text(value)
                .font(.body.weight(.medium))
                .foregroundStyle(Color.black.opacity(0.84))
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

#Preview {
    LiveGuideOverlay(guide: GuidePreset.defaults[0], captureCount: 3)
        .padding()
}
