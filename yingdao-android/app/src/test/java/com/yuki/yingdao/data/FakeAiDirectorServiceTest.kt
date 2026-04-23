package com.yuki.yingdao.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiDirectorServiceTest {
    private val service = FakeAiDirectorService()

    @Test
    fun generateDirectorPlanForSoloRushShootPrioritizesSelfCaptureFriendlyShots() = runTest {
        val brief = CreativeBrief(
            title = "一个人的校园一天",
            theme = "赶课间隙快速记录",
            style = "青春纪实",
            durationSec = 30,
            castCount = 1,
            locations = listOf("教室", "校园小路"),
            shootGoal = "快速记录今天的校园状态",
            mood = "轻快",
            highlightSubject = "我自己",
            soloMode = true,
            timePressure = TimePressure.High,
        )

        val plan = service.generateDirectorPlan(brief).getOrThrow()

        assertTrue(plan.storyLogline.contains("一个人") || plan.storyLogline.contains("单人"))
        assertTrue(plan.shotTasks.size <= 6)
        assertTrue(plan.shotTasks.none { it.title.contains("同伴") || it.goal.contains("互动") })
        assertTrue(plan.shotTasks.all { it.successChecklist.isNotEmpty() })
        assertTrue(plan.shotTasks.all { it.whyThisShotMatters.isNotBlank() })
    }

    @Test
    fun generateDirectorPlanVariesByBriefIntent() = runTest {
        val diaryBrief = CreativeBrief(
            theme = "安静自习日常",
            shootGoal = "记录普通但真实的一天",
            mood = "安静",
            highlightSubject = "学习状态",
            soloMode = true,
            timePressure = TimePressure.Medium,
        )
        val energeticBrief = CreativeBrief(
            theme = "社团活动记录",
            shootGoal = "拍出热闹和参与感",
            mood = "热闹",
            highlightSubject = "活动氛围",
            soloMode = false,
            timePressure = TimePressure.Low,
            castCount = 3,
        )

        val diaryPlan = service.generateDirectorPlan(diaryBrief).getOrThrow()
        val energeticPlan = service.generateDirectorPlan(energeticBrief).getOrThrow()

        assertNotEquals(diaryPlan.storyLogline, energeticPlan.storyLogline)
        assertNotEquals(diaryPlan.beatSummary, energeticPlan.beatSummary)
        assertNotEquals(diaryPlan.shotTasks.map { it.title }, energeticPlan.shotTasks.map { it.title })
    }

    @Test
    fun reviewClipExplainsWhyRetakeIsNeededForEarlyAttempt() = runTest {
        val shot = ShotTask(
            id = "shot_01",
            orderIndex = 1,
            title = "单人开场",
            goal = "先让观众知道今天是谁在记录",
            shotType = "中景",
            durationSuggestSec = 4,
            compositionHint = "人物位于画面三分之一",
            actionHint = "边走边看前方",
            whyThisShotMatters = "建立主角和当天记录视角",
            successChecklist = listOf("主体清晰", "画面稳定"),
            difficultyHint = "手持时注意呼吸稳定",
            retakePriority = RetakePriority.High,
        )

        val review = service.reviewClip(shot, attemptNumber = 1).getOrThrow()

        assertTrue(review.stabilityScore > 0)
        assertTrue(review.subjectScore > 0)
        assertTrue(review.compositionScore > 0)
        assertTrue(review.emotionScore > 0)
        assertTrue(review.keepReason.isNotBlank() || review.retakeReason.isNotBlank())
        assertTrue(review.nextAction.isNotBlank())
        assertFalse(review.issues.isEmpty())
    }

    @Test
    fun buildAssemblyHighlightsMissingStoryBeats() = runTest {
        val brief = CreativeBrief(
            shootGoal = "记录普通的一天",
            mood = "温和",
            highlightSubject = "校园氛围",
            soloMode = true,
            timePressure = TimePressure.Medium,
        )
        val plan = service.generateDirectorPlan(brief).getOrThrow()
        val approvedOpening = plan.shotTasks.first().copy(
            status = ShotStatus.Approved,
            capturedClipIds = listOf("clip_1"),
        )
        val partialPlan = plan.copy(
            shotTasks = listOf(approvedOpening) + plan.shotTasks.drop(1),
        )
        val project = Project(
            id = "proj_1",
            title = "测试项目",
            templateId = "campus_life",
            status = ProjectStatus.ReviewReady,
            brief = brief,
            directorPlan = partialPlan,
            clips = listOf(
                ClipAsset(
                    id = "clip_1",
                    shotTaskId = approvedOpening.id,
                    localPath = "content://clip_1",
                    durationSec = 3.5,
                    thumbnailLabel = approvedOpening.title,
                    review = service.reviewClip(approvedOpening, 1).getOrThrow(),
                ),
            ),
        )

        val suggestion = service.buildAssembly(project).getOrThrow()

        assertEquals(listOf("clip_1"), suggestion.orderedClipIds)
        assertTrue(suggestion.missingShotIds.isNotEmpty())
        assertTrue(suggestion.missingBeatLabels.isNotEmpty())
        assertTrue(suggestion.editingDirection.isNotBlank())
        assertTrue(suggestion.selectionReasonByClipId.containsKey("clip_1"))
    }
}
