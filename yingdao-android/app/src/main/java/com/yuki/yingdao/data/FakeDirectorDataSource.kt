package com.yuki.yingdao.data

interface AiDirectorService {
    suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan>
    suspend fun reviewClip(
        shotTask: ShotTask,
        attemptNumber: Int,
        mediaType: MediaType,
        localPath: String? = null,
    ): Result<ClipReview>
    suspend fun buildAssembly(project: Project): Result<AssemblySuggestion>
}

class FakeAiDirectorService : AiDirectorService {
    override suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan> {
        val beatSummary = buildBeatSummary(brief)
        val shotTasks = buildShotTasks(brief)
        return Result.success(
            DirectorPlan(
                title = brief.title,
                storyLogline = buildStoryLogline(brief),
                beatSummary = beatSummary,
                shotTasks = shotTasks,
            ),
        )
    }

    override suspend fun reviewClip(
        shotTask: ShotTask,
        attemptNumber: Int,
        mediaType: MediaType,
        localPath: String?,
    ): Result<ClipReview> {
        val attemptPenalty = (attemptNumber - 1).coerceAtLeast(0) * 4
        val baseScore = when (shotTask.retakePriority) {
            RetakePriority.High -> 72
            RetakePriority.Medium -> 78
            RetakePriority.Low -> 84
        }
        val stabilityScore = (baseScore - attemptPenalty).coerceIn(60, 94)
        val subjectScore = (baseScore + 4 - attemptPenalty).coerceIn(62, 96)
        val compositionScore = (baseScore + 2 - attemptPenalty).coerceIn(61, 95)
        val emotionScore = (baseScore + if (shotTask.beatLabel == "情绪记忆点") 6 else 1 - attemptPenalty).coerceIn(60, 96)
        val score = ((stabilityScore + subjectScore + compositionScore + emotionScore) / 4.0).toInt()
        val usable = score >= 80
        val isPhotoTask = mediaType == MediaType.Photo || shotTask.shotType.contains("照片") || shotTask.durationSuggestSec <= 1
        val issues = buildList {
            if (stabilityScore < 78) add(if (isPhotoTask) "画面清晰度还可以再稳一点" else "画面还不够稳")
            if (subjectScore < 80) add("主体还可以再明确")
            if (compositionScore < 80) add("构图重心可以再收紧")
            if (emotionScore < 80) add("情绪记忆点还不够强")
            if (isEmpty()) add(if (isPhotoTask) "这张已经达到了当前拍照目标" else "这一条已经达到了当前镜头目标")
        }
        val keepReason = if (usable) {
            if (isPhotoTask) {
                "这张素材已经完成“${shotTask.whyThisShotMatters}”，可以进入候选照片。"
            } else {
                "这条素材已经完成“${shotTask.whyThisShotMatters}”，可以进入候选片段。"
            }
        } else {
            ""
        }
        val retakeReason = if (usable) {
            ""
        } else {
            val unit = if (isPhotoTask) "照片" else "镜头"
            "当前最影响保留的是：${issues.first()}，而这张$unit 承担的是“${shotTask.beatLabel}”。"
        }
        val nextAction = if (usable) {
            when (shotTask.retakePriority) {
                RetakePriority.High -> if (isPhotoTask) "这张先保留，再补一张更干净的版本会更保险。" else "这条先保留，再补一条更稳版本会更保险。"
                RetakePriority.Medium -> if (isPhotoTask) "这张可以先通过，继续拍下一张。" else "这条可以先通过，继续推进下一个镜头。"
                RetakePriority.Low -> if (isPhotoTask) "这张已经可以直接留作候选照片。" else "这条已经可以直接留作候选素材。"
            }
        } else {
            shotTask.successChecklist.firstOrNull()?.let { "优先补到：$it。" }
                ?: if (isPhotoTask) "建议再拍一张更清楚的版本。" else "建议再拍一条更稳的版本。"
        }
        val suggestion = if (usable) {
            if (isPhotoTask) "已经可用，重点看是否还想补一张更有层次的版本。" else "已经可用，重点看是否还想补一个更有层次的版本。"
        } else {
            if (isPhotoTask) "建议补拍，先把当前照片的关键交付补齐。" else "建议补拍，先把当前镜头的关键交付补齐。"
        }
        return Result.success(
            ClipReview(
                clipId = "",
                usable = usable,
                score = score,
                issues = issues,
                suggestion = suggestion,
                stabilityScore = stabilityScore,
                subjectScore = subjectScore,
                compositionScore = compositionScore,
                emotionScore = emotionScore,
                keepReason = keepReason,
                retakeReason = retakeReason,
                nextAction = nextAction,
            ),
        )
    }

    override suspend fun buildAssembly(project: Project): Result<AssemblySuggestion> {
        val plan = project.directorPlan
        val shotTasks = plan?.shotTasks.orEmpty()
        val approvedShots = shotTasks.filter { it.status == ShotStatus.Approved }
        val orderedClipIds = approvedShots.flatMap { it.capturedClipIds.takeLast(1) }
        val missingShots = shotTasks.filter {
            it.status != ShotStatus.Approved && it.status != ShotStatus.Skipped
        }
        val missingBeatLabels = missingShots.map { it.beatLabel }.distinct()
        val selectionReasonByClipId = approvedShots
            .mapNotNull { shot ->
                shot.capturedClipIds.lastOrNull()?.let { clipId ->
                    clipId to "保留它是因为它承担了“${shot.beatLabel}”，并完成了“${shot.whyThisShotMatters}”。"
                }
            }
            .toMap()

        val isPhotoProject = project.brief.mediaType == MediaType.Photo
        return Result.success(
            AssemblySuggestion(
                orderedClipIds = orderedClipIds,
                missingShotIds = missingShots.map { it.id },
                titleOptions = buildTitleOptions(project.brief),
                captionDraft = buildCaptionDraft(project.brief),
                missingBeatLabels = missingBeatLabels,
                editingDirection = if (missingBeatLabels.isEmpty()) {
                    if (isPhotoProject) {
                        "当前照片已经覆盖了环境、主体、细节、情绪和收尾，可以按这个顺序直接挑图发布。"
                    } else {
                        "当前镜头已经覆盖了开场、人物和收尾，可以按环境 → 人物 → 情绪 → 收束的顺序直接成片。"
                    }
                } else {
                    if (isPhotoProject) {
                        "建议先补齐 ${missingBeatLabels.joinToString("、")}，再做挑图收束，这样照片组会更完整。"
                    } else {
                        "建议先补齐 ${missingBeatLabels.joinToString("、")}，再做成片收束，这样故事会更完整。"
                    }
                },
                selectionReasonByClipId = selectionReasonByClipId,
            ),
        )
    }

    private fun buildStoryLogline(brief: CreativeBrief): String {
        val subject = brief.highlightSubject.ifBlank { "今天最想留下的瞬间" }
        val subjectPrefix = if (brief.soloMode || brief.castCount <= 1) "一个人" else "一群人"
        val pace = when (brief.timePressure) {
            TimePressure.High -> if (brief.mediaType == MediaType.Photo) "用更少但更明确的照片" else "用更利落的镜头节奏"
            TimePressure.Medium -> if (brief.mediaType == MediaType.Photo) "用清晰的照片顺序" else "用清晰的镜头推进"
            TimePressure.Low -> if (brief.mediaType == MediaType.Photo) "用更舒展的照片层次" else "用更舒展的镜头层次"
        }
        val output = if (brief.mediaType == MediaType.Photo) "照片组" else "影像记录"
        return "$subjectPrefix 围绕“$subject”展开记录，${pace}拍出“${brief.mood}”的${brief.theme}$output。"
    }

    private fun buildBeatSummary(brief: CreativeBrief): List<String> {
        if (brief.mediaType == MediaType.Photo) {
            return listOf("先拍清环境和主题", "补足主体、细节和情绪", "用一张氛围照收住今天")
        }
        val opening = if (brief.timePressure == TimePressure.High) {
            "快速建立场景和主角状态"
        } else {
            "先建立环境和当天情绪"
        }
        val middle = if (brief.soloMode || brief.castCount <= 1) {
            "用单人动作和细节把故事往前推"
        } else {
            "用人物互动和细节把故事往前推"
        }
        val closing = if (brief.needVoiceover) {
            "给片尾留出一句旁白和收束镜头"
        } else {
            "用情绪收尾镜头把今天停住"
        }
        return listOf(opening, middle, closing)
    }

    private fun buildShotTasks(brief: CreativeBrief): List<ShotTask> {
        if (brief.mediaType == MediaType.Photo) {
            return buildPhotoTasks(brief)
        }
        val shots = mutableListOf<ShotTask>()
        val quickMode = brief.timePressure == TimePressure.High
        val singleMode = brief.soloMode || brief.castCount <= 1
        val baseDuration = when (brief.timePressure) {
            TimePressure.High -> 3
            TimePressure.Medium -> 4
            TimePressure.Low -> 5
        }

        shots += ShotTask(
            id = "shot_01",
            orderIndex = 1,
            title = if (singleMode) "单人状态开场" else "校园氛围开场",
            goal = if (singleMode) "先让观众知道今天是谁在记录" else "先建立今天的校园氛围和主角关系",
            shotType = if (singleMode) "中景" else "远景",
            durationSuggestSec = baseDuration,
            compositionHint = if (singleMode) "人物位于画面三分之一，给前进方向留白" else "保留建筑、道路和人物层次",
            actionHint = if (singleMode) "边走边进入画面，不要急着看镜头" else "平稳扫过主场景，让人物自然进入",
            beatLabel = "开场建立",
            whyThisShotMatters = "让观众快速知道这条片子在记录谁、记录什么",
            successChecklist = listOf("主体清晰", "画面稳定"),
            difficultyHint = "第一条镜头最容易着急，宁可慢一点。",
            retakePriority = RetakePriority.High,
        )

        shots += ShotTask(
            id = "shot_02",
            orderIndex = 2,
            title = if (brief.highlightSubject.contains("学习")) "学习细节推进" else "动作细节推进",
            goal = "用细节镜头把主角状态和今天的节奏补出来",
            shotType = "近景",
            durationSuggestSec = baseDuration,
            compositionHint = "靠近最能代表今天状态的动作细节",
            actionHint = if (brief.highlightSubject.contains("学习")) "抓翻页、写字、敲键盘的动作" else "抓最能代表今天状态的手部或身体动作",
            beatLabel = "状态推进",
            whyThisShotMatters = "让观众不只是看到人，还能看到当下在发生什么",
            successChecklist = listOf("动作被拍完整", "细节主体明确"),
            difficultyHint = "细节镜头不要贪多，优先拍最能代表今天的一处。",
            retakePriority = RetakePriority.High,
        )

        shots += ShotTask(
            id = "shot_03",
            orderIndex = 3,
            title = if (singleMode) "环境关系镜头" else "同伴关系镜头",
            goal = if (singleMode) "把人物和校园空间的关系拍出来" else "把人物关系和互动感拍出来",
            shotType = if (singleMode) "中远景" else "双人中景",
            durationSuggestSec = baseDuration,
            compositionHint = if (singleMode) "人物和环境都要清楚，不要只剩背影" else "两个人物不要贴边，预留互动空间",
            actionHint = if (singleMode) "让人物走、停、回头中的一个动作自然发生" else "边走边聊、一起看手机或击掌都可以",
            beatLabel = "关系建立",
            whyThisShotMatters = if (singleMode) "让这条片子不只是特写堆叠，而有真实空间感" else "让观众感到这是一段有陪伴感的校园生活",
            successChecklist = listOf("人物与环境关系清楚", "动作自然不僵"),
            difficultyHint = "关系镜头不要抢拍，等动作自然发生再录。",
            retakePriority = if (singleMode) RetakePriority.Medium else RetakePriority.High,
        )

        shots += ShotTask(
            id = "shot_04",
            orderIndex = 4,
            title = "情绪记忆点",
            goal = "补一条让人记住今天情绪的镜头",
            shotType = "特写",
            durationSuggestSec = if (quickMode) 3 else baseDuration,
            compositionHint = "背景尽量简洁，把情绪集中在人物表情或动作停顿上",
            actionHint = "抓住停顿、抬头、回头或轻微微笑的一瞬间",
            beatLabel = "情绪记忆点",
            whyThisShotMatters = "让这条片子有记忆点，而不只是流程记录",
            successChecklist = listOf("情绪主体明确", "画面不要晃"),
            difficultyHint = "情绪镜头最怕刻意，先让动作自然发生。",
            retakePriority = RetakePriority.Medium,
        )

        shots += ShotTask(
            id = "shot_05",
            orderIndex = 5,
            title = "校园收束镜头",
            goal = "给成片一个能落下来的结尾",
            shotType = "远景",
            durationSuggestSec = baseDuration,
            compositionHint = "画面尽量干净，保留可放标题或片尾文案的位置",
            actionHint = "镜头最后两秒尽量稳定，给收尾留呼吸空间",
            beatLabel = "结尾收束",
            whyThisShotMatters = "让今天的记录有真正的结束，而不是突然停住",
            successChecklist = listOf("最后两秒稳定", "画面留有结尾空间"),
            difficultyHint = "收尾镜头不要急着停，给自己多留一秒。",
            retakePriority = RetakePriority.High,
        )

        if (!quickMode) {
            shots += ShotTask(
                id = "shot_06",
                orderIndex = 6,
                title = if (singleMode) "补充空镜" else "补充互动切片",
                goal = "给后期剪辑补一个过渡层",
                shotType = if (singleMode) "静态空镜" else "中近景",
                durationSuggestSec = baseDuration,
                compositionHint = "优先补最能连接前后两条镜头的画面",
                actionHint = if (singleMode) "拍风、树影、走廊或路面的流动感" else "抓一个自然的动作切片作为转场",
                beatLabel = "过渡补强",
                whyThisShotMatters = "让镜头之间接得更顺，不会只剩硬切",
                successChecklist = listOf("画面可作为过渡", "节奏稳定"),
                difficultyHint = "补镜头时不要偏离主主题。",
                retakePriority = RetakePriority.Low,
            )
        }

        return shots
    }

    private fun buildPhotoTasks(brief: CreativeBrief): List<ShotTask> {
        val themeProfile = photoThemeProfile(brief)
        val singleMode = brief.soloMode || brief.castCount <= 1
        val detailAction = when (themeProfile) {
            PhotoThemeProfile.Food -> "拍一张食物近景，保留蒸汽、酱汁、切面或餐具细节"
            PhotoThemeProfile.Pet -> "拍一张宠物表情或动作特写，等它自然看向光线"
            PhotoThemeProfile.Travel -> "拍一张路标、街角、车票或地标细节"
            PhotoThemeProfile.Outfit -> "拍一张配饰、鞋包、衣料纹理或手部动作"
            PhotoThemeProfile.Campus -> "拍一张书本、走廊、课桌或操场细节"
            PhotoThemeProfile.Daily -> "拍一张最能代表今天的小物或动作细节"
        }
        val subjectTitle = when (themeProfile) {
            PhotoThemeProfile.Food -> "主体美食照"
            PhotoThemeProfile.Pet -> "宠物主体照"
            PhotoThemeProfile.Travel -> "路上主体照"
            PhotoThemeProfile.Outfit -> "人物穿搭照"
            PhotoThemeProfile.Campus -> "校园主体照"
            PhotoThemeProfile.Daily -> "日常主体照"
        }
        val relationshipGoal = if (singleMode) {
            "把主体和所在环境的关系拍出来，照片不要只剩局部"
        } else {
            "把人物之间或人与场景之间的关系拍出来，让照片有生活感"
        }
        return listOf(
            ShotTask(
                id = "shot_01",
                orderIndex = 1,
                title = "环境建立照",
                goal = "先拍一张能说明“${brief.theme}”发生在哪里的照片",
                shotType = "照片 / 环境",
                durationSuggestSec = 1,
                compositionHint = "保留环境层次，把主体放在画面三分之一附近",
                actionHint = "先站稳再拍一张，画面里要看得出地点和氛围",
                beatLabel = "环境建立",
                whyThisShotMatters = "让这组照片一开始就有场景，而不是零散素材",
                successChecklist = listOf("环境信息清楚", "主体位置明确"),
                difficultyHint = "第一张不要急着贴近，先给环境一点空间。",
                retakePriority = RetakePriority.High,
            ),
            ShotTask(
                id = "shot_02",
                orderIndex = 2,
                title = subjectTitle,
                goal = "拍清“${brief.highlightSubject}”，让这组照片有明确主角",
                shotType = "照片 / 主体",
                durationSuggestSec = 1,
                compositionHint = "背景尽量干净，主体不要贴边",
                actionHint = if (themeProfile == PhotoThemeProfile.Outfit && !singleMode) {
                    "请对方轻微转身或整理衣角，抓自然的一张"
                } else {
                    "等主体自然进入最好看的状态，再拍一张"
                },
                beatLabel = "主体明确",
                whyThisShotMatters = "日常照片最先要让人知道你想留下什么",
                successChecklist = listOf("主体清晰", "背景不抢戏"),
                difficultyHint = "主体照不需要复杂，先拍清楚比拍花哨更重要。",
                retakePriority = RetakePriority.High,
            ),
            ShotTask(
                id = "shot_03",
                orderIndex = 3,
                title = "细节特写照",
                goal = "用一张细节照片补足今天最有记忆点的部分",
                shotType = "照片 / 特写",
                durationSuggestSec = 1,
                compositionHint = "靠近细节，画面只保留一到两个重点",
                actionHint = detailAction,
                beatLabel = "细节补强",
                whyThisShotMatters = "细节会让这组照片更像真实生活，而不是打卡照",
                successChecklist = listOf("细节主体明确", "光线干净"),
                difficultyHint = "特写不要贪多，靠近一个最有代表性的细节。",
                retakePriority = RetakePriority.Medium,
            ),
            ShotTask(
                id = "shot_04",
                orderIndex = 4,
                title = if (singleMode) "情绪瞬间照" else "关系瞬间照",
                goal = relationshipGoal,
                shotType = "照片 / 情绪",
                durationSuggestSec = 1,
                compositionHint = "把表情、动作或互动留在画面中心附近",
                actionHint = "不要摆太久，抓停顿、抬头、转身或轻微笑的一瞬间",
                beatLabel = if (singleMode) "情绪记忆点" else "关系建立",
                whyThisShotMatters = "让照片里有当下的状态，而不只是物和场景",
                successChecklist = listOf("动作自然", "情绪主体明确"),
                difficultyHint = "情绪照片最怕刻意，先让动作自然发生。",
                retakePriority = RetakePriority.Medium,
            ),
            ShotTask(
                id = "shot_05",
                orderIndex = 5,
                title = "收尾氛围照",
                goal = "最后拍一张能给这组照片收住情绪的画面",
                shotType = "照片 / 氛围",
                durationSuggestSec = 1,
                compositionHint = "画面留一点空白，适合之后放标题或文案",
                actionHint = "退后一步拍环境、桌面、背影或光线，让今天有一个结束感",
                beatLabel = "结尾收束",
                whyThisShotMatters = "让这组照片有完整的开始和结束",
                successChecklist = listOf("氛围完整", "画面留白舒服"),
                difficultyHint = "收尾照可以安静一点，不需要再塞很多信息。",
                retakePriority = RetakePriority.Low,
            ),
        )
    }

    private fun photoThemeProfile(brief: CreativeBrief): PhotoThemeProfile {
        val text = "${brief.theme} ${brief.highlightSubject} ${brief.locations.joinToString(" ")}"
        return when {
            listOf("美食", "餐", "咖啡", "探店", "甜点").any { text.contains(it) } -> PhotoThemeProfile.Food
            listOf("宠物", "猫", "狗", "毛孩子").any { text.contains(it) } -> PhotoThemeProfile.Pet
            listOf("旅行", "城市", "散步", "city", "旅途", "街").any { text.contains(it, ignoreCase = true) } -> PhotoThemeProfile.Travel
            listOf("穿搭", "自拍", "衣", "鞋", "头像").any { text.contains(it) } -> PhotoThemeProfile.Outfit
            listOf("校园", "图书馆", "操场", "教室", "学习").any { text.contains(it) } -> PhotoThemeProfile.Campus
            else -> PhotoThemeProfile.Daily
        }
    }

    private enum class PhotoThemeProfile {
        Food,
        Pet,
        Travel,
        Outfit,
        Campus,
        Daily,
    }

    private fun buildTitleOptions(brief: CreativeBrief): List<String> {
        return if (brief.mediaType == MediaType.Photo) {
            listOf(
                "${brief.theme}的${brief.mood}时刻",
                "今天的${brief.highlightSubject}",
                "把${brief.theme}拍成一组照片",
            )
        } else {
            listOf(
                "${brief.theme}的${brief.mood}时刻",
                "今天的${brief.highlightSubject}",
                "把${brief.theme}拍成一条小片子",
            )
        }
    }

    private fun buildCaptionDraft(brief: CreativeBrief): List<String> {
        return if (brief.mediaType == MediaType.Photo) {
            listOf(
                "今天想拍下的，不只是${brief.theme}，还有“${brief.highlightSubject}”这一刻的状态。",
                "这组照片想留下的是一种${brief.mood}的生活节奏。",
            )
        } else {
            listOf(
                "今天想拍下的，不只是${brief.theme}，还有“${brief.highlightSubject}”这一刻的状态。",
                "这条片子想留下的是一种${brief.mood}的生活节奏。",
            )
        }
    }
}

object DirectorTemplates {
    val presets = listOf(
        TemplatePreset(
            id = "daily_photo",
            title = "日常随手拍",
            subtitle = "把今天值得留住的小瞬间拍清楚",
            description = "适合记录普通一天、朋友圈照片和生活碎片。",
            defaultTheme = "今天的生活片段",
            defaultStyle = "生活感",
            defaultLocations = listOf("家里", "街边", "咖啡店"),
            defaultHighlightSubject = "今天最想留下的瞬间",
            defaultShootGoal = "拍出一组今天就能分享的日常照片",
            defaultMediaType = MediaType.Photo,
        ),
        TemplatePreset(
            id = "food_memory",
            title = "美食与餐桌",
            subtitle = "把食物、环境和一起吃饭的人拍出氛围",
            description = "适合探店、家常饭、咖啡甜点和聚餐记录。",
            defaultTheme = "美食与餐桌记录",
            defaultStyle = "干净明亮",
            defaultLocations = listOf("餐桌", "咖啡店", "街边"),
            defaultHighlightSubject = "食物细节和用餐氛围",
            defaultShootGoal = "拍出一组看起来有食欲、也有生活感的照片",
            defaultMediaType = MediaType.Photo,
        ),
        TemplatePreset(
            id = "travel_walk",
            title = "城市散步 / 旅行记录",
            subtitle = "把路线、地标和路上的情绪串起来",
            description = "适合 city walk、周末出游和旅行随拍。",
            defaultTheme = "城市散步随拍",
            defaultStyle = "胶片感",
            defaultLocations = listOf("街边", "公园", "旅途中"),
            defaultHighlightSubject = "路上的风景和人的状态",
            defaultShootGoal = "拍出一组能记住这段路线和心情的照片",
            defaultMediaType = MediaType.Photo,
        ),
        TemplatePreset(
            id = "pet_life",
            title = "宠物日常",
            subtitle = "抓住宠物动作、表情和陪伴感",
            description = "适合猫狗日常、陪玩、散步和睡觉瞬间。",
            defaultTheme = "宠物日常记录",
            defaultStyle = "治愈",
            defaultLocations = listOf("家里", "公园", "街边"),
            defaultHighlightSubject = "宠物的表情和动作瞬间",
            defaultShootGoal = "拍出一组自然、有陪伴感的宠物照片",
            defaultMediaType = MediaType.Photo,
        ),
        TemplatePreset(
            id = "outfit_selfie",
            title = "穿搭 / 自拍",
            subtitle = "先拍清人，再补足细节和氛围",
            description = "适合今日穿搭、头像、自拍和请朋友帮拍。",
            defaultTheme = "今日穿搭记录",
            defaultStyle = "轻复古",
            defaultLocations = listOf("家里", "街边", "商场"),
            defaultHighlightSubject = "人的状态和穿搭细节",
            defaultShootGoal = "拍出一组自然、不僵硬、能直接挑图的照片",
            defaultMediaType = MediaType.Photo,
        ),
        TemplatePreset(
            id = "campus_life",
            title = "校园生活",
            subtitle = "学习、操场、食堂和同伴互动都能拍",
            description = "适合把校园日常拍成照片组或短片。",
            defaultTheme = "校园生活记录",
            defaultStyle = "清新温暖",
            defaultLocations = listOf("操场", "图书馆", "食堂"),
            defaultHighlightSubject = "我的校园状态",
            defaultShootGoal = "拍出一组能分享的校园日常影像",
            defaultMediaType = MediaType.Photo,
        ),
    )
}
