package com.yuki.yingdao.data

enum class ProjectStatus {
    Draft,
    BriefReady,
    ShotPlanReady,
    Shooting,
    ReviewReady,
    AssemblyReady,
}

enum class ShotStatus {
    Planned,
    Active,
    Captured,
    Approved,
    RetakeSuggested,
    Skipped,
}

enum class TimePressure {
    Low,
    Medium,
    High,
}

enum class RetakePriority {
    Low,
    Medium,
    High,
}

data class TemplatePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
)

data class CreativeBrief(
    val title: String = "我的校园生活记录",
    val theme: String = "校园散步的一天",
    val style: String = "清新温暖",
    val durationSec: Int = 60,
    val castCount: Int = 1,
    val locations: List<String> = listOf("操场", "图书馆", "食堂"),
    val needCaption: Boolean = true,
    val needVoiceover: Boolean = false,
    val shootGoal: String = "拍出一条今天就能分享的校园短片",
    val mood: String = "温和",
    val highlightSubject: String = "我的校园状态",
    val soloMode: Boolean = false,
    val timePressure: TimePressure = TimePressure.Medium,
)

data class DirectorPlan(
    val title: String,
    val storyLogline: String,
    val beatSummary: List<String>,
    val shotTasks: List<ShotTask>,
)

data class ShotTask(
    val id: String,
    val orderIndex: Int,
    val title: String,
    val goal: String,
    val shotType: String,
    val durationSuggestSec: Int,
    val compositionHint: String,
    val actionHint: String,
    val status: ShotStatus = ShotStatus.Planned,
    val capturedClipIds: List<String> = emptyList(),
    val latestReview: ClipReview? = null,
    val beatLabel: String = "故事推进",
    val whyThisShotMatters: String = "让这条片子更完整。",
    val successChecklist: List<String> = emptyList(),
    val difficultyHint: String = "保持动作自然即可。",
    val retakePriority: RetakePriority = RetakePriority.Medium,
)

data class ClipReview(
    val clipId: String,
    val usable: Boolean,
    val score: Int,
    val issues: List<String>,
    val suggestion: String,
    val stabilityScore: Int,
    val subjectScore: Int,
    val compositionScore: Int,
    val emotionScore: Int,
    val keepReason: String,
    val retakeReason: String,
    val nextAction: String,
)

data class ClipAsset(
    val id: String,
    val shotTaskId: String,
    val localPath: String,
    val durationSec: Double,
    val thumbnailLabel: String,
    val review: ClipReview? = null,
)

data class AssemblySuggestion(
    val orderedClipIds: List<String>,
    val missingShotIds: List<String>,
    val titleOptions: List<String>,
    val captionDraft: List<String>,
    val missingBeatLabels: List<String>,
    val editingDirection: String,
    val selectionReasonByClipId: Map<String, String>,
)

data class Project(
    val id: String,
    val title: String,
    val templateId: String,
    val status: ProjectStatus,
    val brief: CreativeBrief,
    val directorPlan: DirectorPlan?,
    val clips: List<ClipAsset>,
    val assemblySuggestion: AssemblySuggestion? = null,
)
