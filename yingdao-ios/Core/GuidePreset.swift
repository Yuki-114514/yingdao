import Foundation

public enum RetakePriority: String, Hashable, Sendable {
    case low
    case medium
    case high
}

public enum StoryRole: String, Hashable, Sendable {
    case opening = "开场建立"
    case moment = "状态推进"
    case relationship = "关系建立"

    public var displayTitle: String { rawValue }
}

public struct GuidePreset: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let framingTip: String
    public let actionTip: String
    public let overlayHint: String
    public let storyRole: StoryRole
    public let whyThisShotMatters: String
    public let successChecklist: [String]
    public let difficultyHint: String
    public let retakePriority: RetakePriority

    public init(
        id: String,
        title: String,
        subtitle: String,
        framingTip: String,
        actionTip: String,
        overlayHint: String,
        storyRole: StoryRole,
        whyThisShotMatters: String,
        successChecklist: [String],
        difficultyHint: String,
        retakePriority: RetakePriority,
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.framingTip = framingTip
        self.actionTip = actionTip
        self.overlayHint = overlayHint
        self.storyRole = storyRole
        self.whyThisShotMatters = whyThisShotMatters
        self.successChecklist = successChecklist
        self.difficultyHint = difficultyHint
        self.retakePriority = retakePriority
    }
}

public extension GuidePreset {
    static let defaults: [GuidePreset] = [
        GuidePreset(
            id: "campus-wide",
            title: "校园开场",
            subtitle: "用远景建立一天的情绪基调",
            framingTip: "保留建筑和天空层次，把主体放在画面下三分之一。",
            actionTip: "匀速横移，让操场、教学楼或树影慢慢进入画面。",
            overlayHint: "先拍一条稳住节奏的开场空镜。",
            storyRole: .opening,
            whyThisShotMatters: "让观众先知道这条片发生在什么校园空间里。",
            successChecklist: ["画面稳定", "环境层次清楚"],
            difficultyHint: "开场最容易着急，宁可慢一点也不要急停。",
            retakePriority: .high,
        ),
        GuidePreset(
            id: "study-detail",
            title: "学习细节",
            subtitle: "把手部动作和笔记细节拍出来",
            framingTip: "镜头贴近桌面，突出手、笔记本和翻页动作。",
            actionTip: "慢慢推进，抓住翻页、勾画、敲键盘的瞬间。",
            overlayHint: "优先捕捉最能代表校园感的细节动作。",
            storyRole: .moment,
            whyThisShotMatters: "让观众不只看到人，还能看到当下在发生什么。",
            successChecklist: ["动作被拍完整", "细节主体明确"],
            difficultyHint: "细节镜头不要贪多，优先拍最能代表今天状态的一处。",
            retakePriority: .high,
        ),
        GuidePreset(
            id: "friend-moment",
            title: "同伴互动",
            subtitle: "让画面里出现关系和陪伴感",
            framingTip: "两个人物不要贴边，留出走路或转头的空间。",
            actionTip: "边走边聊、递饮料、一起看手机都很自然。",
            overlayHint: "这条镜头更看重情绪，宁可慢一点也别太僵。",
            storyRole: .relationship,
            whyThisShotMatters: "让观众感到这是一段有陪伴感的校园生活。",
            successChecklist: ["互动自然", "人物关系清楚"],
            difficultyHint: "等动作自然发生再拍，不要为了镜头硬摆。",
            retakePriority: .medium,
        ),
    ]
}
