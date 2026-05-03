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
        GuidePreset(
            id: "food-detail",
            title: "美食细节",
            subtitle: "把食物质感和餐桌氛围拍清楚",
            framingTip: "靠近主体，保留餐具和桌面边缘，让画面有层次。",
            actionTip: "先拍一张最有食欲的主角，再补热气、酱汁或切面。",
            overlayHint: "优先让食物清楚、有光泽，背景不要抢主体。",
            storyRole: .moment,
            whyThisShotMatters: "让这组照片一眼能看出今天吃了什么、好在哪里。",
            successChecklist: ["食物主体清晰", "桌面不杂乱"],
            difficultyHint: "不要离得太近导致失焦，先点按主体再拍。",
            retakePriority: .high,
        ),
        GuidePreset(
            id: "city-walk",
            title: "城市散步",
            subtitle: "记录路上的光线、街景和目的地",
            framingTip: "把道路延伸线或店铺招牌放进画面，保留一点前景。",
            actionTip: "看到光影、橱窗、路牌时停一下，拍一张能代表今天路线的照片。",
            overlayHint: "用环境讲故事，不一定要有人入镜。",
            storyRole: .opening,
            whyThisShotMatters: "先交代今天在哪里，后面的细节照片才有上下文。",
            successChecklist: ["地点特征明确", "画面有前后层次"],
            difficultyHint: "街景容易杂，等路人或车辆让出主体再拍。",
            retakePriority: .medium,
        ),
        GuidePreset(
            id: "pet-moment",
            title: "宠物瞬间",
            subtitle: "抓住表情、动作和陪伴感",
            framingTip: "镜头降到宠物视线高度，让眼睛或动作成为画面中心。",
            actionTip: "等它抬头、伸懒腰、靠近你时快速拍一张。",
            overlayHint: "宠物照片先抓表情，不要强求完全摆正。",
            storyRole: .relationship,
            whyThisShotMatters: "宠物的表情和互动最能让日常照片有记忆点。",
            successChecklist: ["眼神或动作清楚", "背景不过度凌乱"],
            difficultyHint: "动作快时先多拍几张，再挑最自然的一张。",
            retakePriority: .high,
        ),
        GuidePreset(
            id: "outfit-selfie",
            title: "穿搭自拍",
            subtitle: "拍清当天造型和自己的状态",
            framingTip: "保留全身比例，镜头略低一点，避开杂乱背景。",
            actionTip: "先站定拍一张完整穿搭，再补包、鞋或配饰细节。",
            overlayHint: "让衣服线条清楚，同时保留一点当天场景。",
            storyRole: .moment,
            whyThisShotMatters: "把今天的自己和穿搭状态留下来，照片更有个人感。",
            successChecklist: ["穿搭完整", "姿态自然"],
            difficultyHint: "自拍时先看边缘有没有切到头脚，再按快门。",
            retakePriority: .medium,
        ),
        GuidePreset(
            id: "home-corner",
            title: "居家角落",
            subtitle: "把房间里的光线和生活感拍出来",
            framingTip: "选择一个有光的角落，保留窗边、桌面或小物件层次。",
            actionTip: "先整理画面里最抢眼的杂物，再拍下今天最舒服的一处。",
            overlayHint: "居家照片要有呼吸感，不必把所有东西都拍进去。",
            storyRole: .opening,
            whyThisShotMatters: "生活空间能快速建立这组日常照片的情绪。",
            successChecklist: ["光线柔和", "主体角落明确"],
            difficultyHint: "室内光线弱时手要稳，宁可靠近窗边拍。",
            retakePriority: .medium,
        ),
        GuidePreset(
            id: "travel-memory",
            title: "旅行纪念",
            subtitle: "把目的地、同行人和小细节串起来",
            framingTip: "让地标或路线信息出现在背景，同时保留人物或随身物件。",
            actionTip: "到达、路上、吃饭、休息各拍一张，形成完整记忆。",
            overlayHint: "旅行照片不只拍景点，也要拍今天怎么经过这里。",
            storyRole: .relationship,
            whyThisShotMatters: "把地点和人连接起来，旅行记录才不只是风景照。",
            successChecklist: ["地点可辨认", "人物或物件有参与感"],
            difficultyHint: "人多时先找干净背景，再等一秒按快门。",
            retakePriority: .medium,
        ),
    ]
}
