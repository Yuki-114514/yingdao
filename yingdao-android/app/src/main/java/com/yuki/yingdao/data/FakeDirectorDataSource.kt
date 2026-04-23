package com.yuki.yingdao.data

interface AiDirectorService {
    suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan>
    suspend fun reviewClip(shotTask: ShotTask, attemptNumber: Int): Result<ClipReview>
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

    override suspend fun reviewClip(shotTask: ShotTask, attemptNumber: Int): Result<ClipReview> {
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
        val issues = buildList {
            if (stabilityScore < 78) add("画面还不够稳")
            if (subjectScore < 80) add("主体还可以再明确")
            if (compositionScore < 80) add("构图重心可以再收紧")
            if (emotionScore < 80) add("情绪记忆点还不够强")
            if (isEmpty()) add("这一条已经达到了当前镜头目标")
        }
        val keepReason = if (usable) {
            "这条素材已经完成“${shotTask.whyThisShotMatters}”，可以进入候选片段。"
        } else {
            ""
        }
        val retakeReason = if (usable) {
            ""
        } else {
            "当前最影响保留的是：${issues.first()}，而这条镜头承担的是“${shotTask.beatLabel}”。"
        }
        val nextAction = if (usable) {
            when (shotTask.retakePriority) {
                RetakePriority.High -> "这条先保留，再补一条更稳版本会更保险。"
                RetakePriority.Medium -> "这条可以先通过，继续推进下一个镜头。"
                RetakePriority.Low -> "这条已经可以直接留作候选素材。"
            }
        } else {
            shotTask.successChecklist.firstOrNull()?.let { "优先补到：$it。" } ?: "建议再拍一条更稳的版本。"
        }
        val suggestion = if (usable) {
            "已经可用，重点看是否还想补一个更有层次的版本。"
        } else {
            "建议补拍，先把当前镜头的关键交付补齐。"
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

        return Result.success(
            AssemblySuggestion(
                orderedClipIds = orderedClipIds,
                missingShotIds = missingShots.map { it.id },
                titleOptions = buildTitleOptions(project.brief),
                captionDraft = buildCaptionDraft(project.brief),
                missingBeatLabels = missingBeatLabels,
                editingDirection = if (missingBeatLabels.isEmpty()) {
                    "当前镜头已经覆盖了开场、人物和收尾，可以按环境 → 人物 → 情绪 → 收束的顺序直接成片。"
                } else {
                    "建议先补齐 ${missingBeatLabels.joinToString("、")}，再做成片收束，这样故事会更完整。"
                },
                selectionReasonByClipId = selectionReasonByClipId,
            ),
        )
    }

    private fun buildStoryLogline(brief: CreativeBrief): String {
        val subject = brief.highlightSubject.ifBlank { "这一天的校园生活" }
        val subjectPrefix = if (brief.soloMode || brief.castCount <= 1) "一个人" else "一群人"
        val pace = when (brief.timePressure) {
            TimePressure.High -> "用更利落的镜头节奏"
            TimePressure.Medium -> "用清晰的镜头推进"
            TimePressure.Low -> "用更舒展的镜头层次"
        }
        return "$subjectPrefix 围绕“$subject”展开记录，${pace}拍出“${brief.mood}”的${brief.theme}。"
    }

    private fun buildBeatSummary(brief: CreativeBrief): List<String> {
        val opening = if (brief.timePressure == TimePressure.High) {
            "快速建立场景和主角状态"
        } else {
            "先建立校园环境和当天情绪"
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

    private fun buildTitleOptions(brief: CreativeBrief): List<String> {
        return listOf(
            "${brief.theme}的${brief.mood}时刻",
            "今天的${brief.highlightSubject}",
            "把${brief.theme}拍成一条小片子",
        )
    }

    private fun buildCaptionDraft(brief: CreativeBrief): List<String> {
        return listOf(
            "今天想拍下的，不只是${brief.theme}，还有“${brief.highlightSubject}”这一刻的状态。",
            "这条片子想留下的是一种${brief.mood}的校园节奏。",
        )
    }
}

object DirectorTemplates {
    val presets = listOf(
        TemplatePreset(
            id = "campus_life",
            title = "校园影像陪练",
            subtitle = "拍前策划、拍中陪练、拍后解释反馈",
            description = "适合不会拍但想把校园日常拍成片的普通用户。",
        ),
    )
}
