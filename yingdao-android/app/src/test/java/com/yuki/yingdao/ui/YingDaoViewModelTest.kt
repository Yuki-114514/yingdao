package com.yuki.yingdao.ui

import com.yuki.yingdao.data.AiDirectorService
import com.yuki.yingdao.data.AssemblySuggestion
import com.yuki.yingdao.data.ClipReview
import com.yuki.yingdao.data.CreativeBrief
import com.yuki.yingdao.data.DirectorPlan
import com.yuki.yingdao.data.DirectorTemplates
import com.yuki.yingdao.data.MediaType
import com.yuki.yingdao.data.Project
import com.yuki.yingdao.data.ProjectStatus
import com.yuki.yingdao.data.RetakePriority
import com.yuki.yingdao.data.ShotStatus
import com.yuki.yingdao.data.ShotTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YingDaoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateDraftStoresNewCoachFields() {
        val viewModel = YingDaoViewModel()

        viewModel.updateDraft(
            shootGoal = "拍出一组可以直接发朋友圈的日常照片",
            mood = "松弛",
            highlightSubject = "放学后的自己",
            soloMode = true,
            timePressure = com.yuki.yingdao.data.TimePressure.High,
            mediaType = MediaType.Photo,
        )

        val draft = viewModel.uiState.draft
        assertEquals("拍出一组可以直接发朋友圈的日常照片", draft.shootGoal)
        assertEquals("松弛", draft.mood)
        assertEquals("放学后的自己", draft.highlightSubject)
        assertTrue(draft.soloMode)
        assertEquals(com.yuki.yingdao.data.TimePressure.High, draft.timePressure)
        assertEquals(MediaType.Photo, draft.mediaType)
    }

    @Test
    fun selectTemplateAppliesDefaultBriefForBroaderDailyThemes() {
        val viewModel = YingDaoViewModel()
        val foodTemplate = DirectorTemplates.presets.first { it.id == "food_memory" }

        viewModel.selectTemplate(foodTemplate.id)

        val draft = viewModel.uiState.draft
        assertEquals(foodTemplate.id, viewModel.uiState.selectedTemplateId)
        assertEquals(foodTemplate.defaultTheme, draft.theme)
        assertEquals(foodTemplate.defaultStyle, draft.style)
        assertEquals(foodTemplate.defaultLocations, draft.locations)
        assertEquals(foodTemplate.defaultHighlightSubject, draft.highlightSubject)
        assertEquals(foodTemplate.defaultShootGoal, draft.shootGoal)
        assertEquals(MediaType.Photo, draft.mediaType)
    }

    @Test
    fun generateDirectorPlanCarriesCoachFieldsIntoProjectBrief() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.updateDraft(
            shootGoal = "记录一天里最有情绪的校园时刻",
            mood = "安静",
            highlightSubject = "图书馆里的学习状态",
            soloMode = true,
            timePressure = com.yuki.yingdao.data.TimePressure.Medium,
        )

        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        val brief = viewModel.uiState.activeProject?.brief
        assertNotNull(brief)
        assertEquals("记录一天里最有情绪的校园时刻", brief?.shootGoal)
        assertEquals("安静", brief?.mood)
        assertEquals("图书馆里的学习状态", brief?.highlightSubject)
        assertEquals(true, brief?.soloMode)
        assertEquals(com.yuki.yingdao.data.TimePressure.Medium, brief?.timePressure)
    }

    @Test
    fun registerRecordedClipForSelectedShotStoresRealClipMetadata() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        val selectedShotId = viewModel.uiState.selectedShot?.id

        viewModel.registerRecordedClipForSelectedShot(
            localPath = "content://media/external/video/media/1",
            durationSec = 4.2,
        )
        advanceUntilIdle()

        val activeProject = viewModel.uiState.activeProject
        val clip = activeProject?.clips?.firstOrNull()
        val updatedShot = activeProject
            ?.directorPlan
            ?.shotTasks
            ?.firstOrNull { it.id == selectedShotId }

        assertNotNull(activeProject)
        assertEquals(1, activeProject?.clips?.size)
        assertEquals(
            "content://media/external/video/media/1",
            clip?.localPath,
        )
        assertEquals(MediaType.Video, clip?.mediaType)
        assertEquals(4.2, clip?.durationSec ?: 0.0, 0.01)
        assertNotNull(updatedShot?.latestReview)
        assertTrue(updatedShot?.capturedClipIds?.isNotEmpty() == true)
        assertTrue(
            updatedShot?.status == ShotStatus.Captured ||
                updatedShot?.status == ShotStatus.RetakeSuggested,
        )
    }

    @Test
    fun registerCapturedPhotoForSelectedShotStoresPhotoMetadata() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        val selectedShotId = viewModel.uiState.selectedShot?.id

        viewModel.registerCapturedPhotoForSelectedShot(
            localPath = "content://media/external/images/media/1",
        )
        advanceUntilIdle()

        val activeProject = viewModel.uiState.activeProject
        val clip = activeProject?.clips?.firstOrNull()
        val updatedShot = activeProject
            ?.directorPlan
            ?.shotTasks
            ?.firstOrNull { it.id == selectedShotId }

        assertNotNull(activeProject)
        assertEquals(1, activeProject?.clips?.size)
        assertEquals("content://media/external/images/media/1", clip?.localPath)
        assertEquals(MediaType.Photo, clip?.mediaType)
        assertEquals(0.0, clip?.durationSec ?: -1.0, 0.01)
        assertNotNull(updatedShot?.latestReview)
        assertTrue(updatedShot?.capturedClipIds?.isNotEmpty() == true)
    }

    @Test
    fun registerCapturedPhotoForSelectedShotPassesLocalPhotoPathToReviewService() = runTest {
        val service = FakeAsyncAiDirectorService()
        val viewModel = YingDaoViewModel(aiDirectorService = service)
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        viewModel.registerCapturedPhotoForSelectedShot(
            localPath = "content://media/external/images/media/9",
        )
        advanceUntilIdle()

        assertEquals(MediaType.Photo, service.lastReviewMediaType)
        assertEquals("content://media/external/images/media/9", service.lastReviewLocalPath)
    }

    @Test
    fun requestRetakeForSelectedShotResetsReviewSurface() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()
        viewModel.registerRecordedClipForSelectedShot(
            localPath = "content://media/external/video/media/2",
            durationSec = 3.1,
        )
        advanceUntilIdle()

        viewModel.requestRetakeForSelectedShot()

        val updatedShot = viewModel.uiState.selectedShot
        assertEquals(ShotStatus.Planned, updatedShot?.status)
        assertEquals(null, updatedShot?.latestReview)
    }

    @Test
    fun approveSelectedShotWithoutUsableClipKeepsShotUnresolved() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        val selectedShotId = viewModel.uiState.selectedShot?.id
        viewModel.approveSelectedShot()

        val shot = viewModel.uiState.activeProject
            ?.directorPlan
            ?.shotTasks
            ?.firstOrNull { it.id == selectedShotId }
        assertEquals(ShotStatus.Planned, shot?.status)
    }

    @Test
    fun resolvingShotsWithApproveOrSkipMarksProjectReviewReady() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()
        val shotCount = viewModel.uiState.activeProject?.directorPlan?.shotTasks?.size ?: 0

        repeat(shotCount) { index ->
            viewModel.registerRecordedClipForSelectedShot(
                localPath = "content://resolve/$index",
                durationSec = 3.0,
            )
            advanceUntilIdle()
            val review = viewModel.uiState.selectedShot?.latestReview
            if (review?.usable == true) {
                viewModel.approveSelectedShot()
            } else {
                viewModel.skipSelectedShot()
            }
        }

        assertEquals(ProjectStatus.ReviewReady, viewModel.uiState.activeProject?.status)
    }

    @Test
    fun buildAssemblySuggestionWithMissingShotsKeepsProjectOutOfOutputState() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        repeat(3) {
            viewModel.skipSelectedShot()
        }
        viewModel.registerRecordedClipForSelectedShot(
            localPath = "content://partial-assembly/usable",
            durationSec = 3.0,
        )
        advanceUntilIdle()
        viewModel.approveSelectedShot()
        viewModel.buildAssemblySuggestion()
        advanceUntilIdle()

        assertEquals(ProjectStatus.Shooting, viewModel.uiState.activeProject?.status)
    }

    @Test
    fun requestRetakeAfterProjectBecomesReviewReadyReturnsProjectToShooting() = runTest {
        val viewModel = YingDaoViewModel()
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        repeat(viewModel.uiState.activeProject?.directorPlan?.shotTasks?.size ?: 0) { index ->
            viewModel.registerRecordedClipForSelectedShot(
                localPath = "content://review-ready/$index",
                durationSec = 3.0,
            )
            advanceUntilIdle()
            val review = viewModel.uiState.selectedShot?.latestReview
            if (review?.usable == true) {
                viewModel.approveSelectedShot()
            } else {
                viewModel.skipSelectedShot()
            }
        }

        val activeProjectId = viewModel.uiState.activeProject?.id
        assertNotNull(activeProjectId)
        viewModel.openProject(activeProjectId!!)
        viewModel.requestRetakeForSelectedShot()

        assertEquals(ProjectStatus.Shooting, viewModel.uiState.activeProject?.status)
    }

    @Test
    fun buildAssemblySuggestionWithoutActiveProjectKeepsLoadingFlagFalse() = runTest {
        val viewModel = YingDaoViewModel()

        viewModel.buildAssemblySuggestion()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.isBuildingAssembly)
    }

    @Test
    fun destinationRouteForProjectStatusReturnsMatchingScreen() {
        assertEquals("plan", destinationRouteFor(ProjectStatus.ShotPlanReady))
        assertEquals("capture", destinationRouteFor(ProjectStatus.Shooting))
        assertEquals("review", destinationRouteFor(ProjectStatus.ReviewReady))
        assertEquals("output", destinationRouteFor(ProjectStatus.AssemblyReady))
        assertEquals("home", destinationRouteFor(ProjectStatus.Draft))
    }

    @Test
    fun destinationRouteForBriefReadyReturnsHomeScreen() {
        assertEquals("home", destinationRouteFor(ProjectStatus.BriefReady))
    }

    @Test
    fun projectStatusLabelReturnsUserFacingCopy() {
        assertEquals("还没开始", projectStatusLabel(ProjectStatus.Draft))
        assertEquals("正在准备", projectStatusLabel(ProjectStatus.BriefReady))
        assertEquals("可以开拍了", projectStatusLabel(ProjectStatus.ShotPlanReady))
        assertEquals("拍摄中", projectStatusLabel(ProjectStatus.Shooting))
        assertEquals("待挑片", projectStatusLabel(ProjectStatus.ReviewReady))
        assertEquals("可以出片了", projectStatusLabel(ProjectStatus.AssemblyReady))
    }

    @Test
    fun generateDirectorPlanSuccessTogglesLoadingAndClearsPlanError() = runTest {
        val viewModel = YingDaoViewModel(
            aiDirectorService = FakeAsyncAiDirectorService(
                generatePlanResult = Result.success(sampleDirectorPlan()),
            ),
        )

        viewModel.generateDirectorPlan()

        assertTrue(viewModel.uiState.isGeneratingPlan)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.isGeneratingPlan)
        assertNull(viewModel.uiState.planError)
        assertEquals("AI 校园短片方案", viewModel.uiState.activeProject?.directorPlan?.title)
    }

    @Test
    fun generateDirectorPlanFailureStoresPlanErrorAndDoesNotCreateProject() = runTest {
        val viewModel = YingDaoViewModel(
            aiDirectorService = FakeAsyncAiDirectorService(
                generatePlanResult = Result.failure(IllegalStateException("plan failed")),
            ),
        )

        viewModel.generateDirectorPlan()

        assertTrue(viewModel.uiState.isGeneratingPlan)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.isGeneratingPlan)
        assertEquals("plan failed", viewModel.uiState.planError)
        assertNull(viewModel.uiState.activeProject)
    }

    @Test
    fun registerRecordedClipSuccessSetsReviewLoadingThenStoresLatestReview() = runTest {
        val review = sampleClipReview(usable = true)
        val viewModel = YingDaoViewModel(
            aiDirectorService = FakeAsyncAiDirectorService(
                reviewResult = Result.success(review),
            ),
        )
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        viewModel.registerRecordedClipForSelectedShot(
            localPath = "content://media/external/video/media/77",
            durationSec = 4.0,
        )

        assertTrue(viewModel.uiState.isReviewingClip)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.isReviewingClip)
        assertNull(viewModel.uiState.reviewError)
        assertEquals(review.score, viewModel.uiState.selectedShot?.latestReview?.score)
    }

    @Test
    fun registerRecordedClipFailureStoresReviewErrorWithoutAdvancingShotStatus() = runTest {
        val viewModel = YingDaoViewModel(
            aiDirectorService = FakeAsyncAiDirectorService(
                reviewResult = Result.failure(IllegalStateException("review failed")),
            ),
        )
        viewModel.generateDirectorPlan()
        advanceUntilIdle()
        val selectedShotId = viewModel.uiState.selectedShot?.id

        viewModel.registerRecordedClipForSelectedShot(
            localPath = "content://media/external/video/media/78",
            durationSec = 4.0,
        )

        assertTrue(viewModel.uiState.isReviewingClip)
        advanceUntilIdle()
        val shot = viewModel.uiState.activeProject
            ?.directorPlan
            ?.shotTasks
            ?.firstOrNull { it.id == selectedShotId }
        assertFalse(viewModel.uiState.isReviewingClip)
        assertEquals("review failed", viewModel.uiState.reviewError)
        assertEquals(ShotStatus.Planned, shot?.status)
        assertNull(shot?.latestReview)
    }

    @Test
    fun buildAssemblyFailureStoresAssemblyErrorWithoutPromotingProjectStatus() = runTest {
        val viewModel = YingDaoViewModel(
            aiDirectorService = FakeAsyncAiDirectorService(
                buildAssemblyResult = Result.failure(IllegalStateException("assembly failed")),
            ),
        )
        viewModel.generateDirectorPlan()
        advanceUntilIdle()

        viewModel.buildAssemblySuggestion()

        assertTrue(viewModel.uiState.isBuildingAssembly)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.isBuildingAssembly)
        assertEquals("assembly failed", viewModel.uiState.assemblyError)
        assertEquals(ProjectStatus.ShotPlanReady, viewModel.uiState.activeProject?.status)
        assertNull(viewModel.uiState.activeProject?.assemblySuggestion)
    }

    private fun sampleDirectorPlan() = DirectorPlan(
        title = "AI 校园短片方案",
        storyLogline = "围绕校园状态展开的一天。",
        beatSummary = listOf("建立环境", "推进状态", "完成收尾"),
        shotTasks = listOf(
            ShotTask(
                id = "shot_test_01",
                orderIndex = 1,
                title = "开场镜头",
                goal = "先交代人物和空间",
                shotType = "中景",
                durationSuggestSec = 4,
                beatLabel = "开场建立",
                whyThisShotMatters = "让观众快速进入状态",
                successChecklist = listOf("人物清晰", "画面稳定"),
                difficultyHint = "手稳一点",
                retakePriority = RetakePriority.High,
                compositionHint = "人物放在三分之一处",
                actionHint = "自然走入画面",
            ),
        ),
    )

    private fun sampleClipReview(usable: Boolean) = ClipReview(
        clipId = "",
        usable = usable,
        score = if (usable) 88 else 72,
        issues = if (usable) listOf("这一条已经达到了当前镜头目标") else listOf("画面还不够稳"),
        suggestion = if (usable) "已经可用，重点看是否还想补一个更有层次的版本。" else "建议补拍，先把当前镜头的关键交付补齐。",
        stabilityScore = if (usable) 86 else 70,
        subjectScore = if (usable) 90 else 74,
        compositionScore = if (usable) 87 else 72,
        emotionScore = if (usable) 89 else 71,
        keepReason = if (usable) "这条可以保留。" else "",
        retakeReason = if (usable) "" else "主体还不够明确。",
        nextAction = if (usable) "继续推进下一个镜头。" else "优先补一条更稳的版本。",
    )

    private class FakeAsyncAiDirectorService(
        private val generatePlanResult: Result<DirectorPlan> = Result.success(sampleFallbackDirectorPlan()),
        private val reviewResult: Result<ClipReview> = Result.success(sampleFallbackClipReview()),
        private val buildAssemblyResult: Result<AssemblySuggestion> = Result.success(sampleFallbackAssemblySuggestion()),
    ) : AiDirectorService {
        override suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan> = generatePlanResult

        override suspend fun reviewClip(
            shotTask: ShotTask,
            attemptNumber: Int,
            mediaType: MediaType,
            localPath: String?,
        ): Result<ClipReview> {
            lastReviewMediaType = mediaType
            lastReviewLocalPath = localPath
            return reviewResult
        }

        override suspend fun buildAssembly(project: Project): Result<AssemblySuggestion> = buildAssemblyResult

        var lastReviewMediaType: MediaType? = null
            private set

        var lastReviewLocalPath: String? = null
            private set
    }

    private companion object {
        fun sampleFallbackDirectorPlan() = DirectorPlan(
            title = "fallback",
            storyLogline = "fallback",
            beatSummary = listOf("fallback"),
            shotTasks = listOf(
                ShotTask(
                    id = "fallback_shot",
                    orderIndex = 1,
                    title = "fallback shot",
                    goal = "fallback goal",
                    shotType = "中景",
                    durationSuggestSec = 3,
                    compositionHint = "fallback composition",
                    actionHint = "fallback action",
                ),
            ),
        )

        fun sampleFallbackClipReview() = ClipReview(
            clipId = "",
            usable = true,
            score = 85,
            issues = listOf("ok"),
            suggestion = "ok",
            stabilityScore = 85,
            subjectScore = 85,
            compositionScore = 85,
            emotionScore = 85,
            keepReason = "ok",
            retakeReason = "",
            nextAction = "ok",
        )

        fun sampleFallbackAssemblySuggestion() = AssemblySuggestion(
            orderedClipIds = emptyList(),
            missingShotIds = emptyList(),
            titleOptions = listOf("fallback"),
            captionDraft = listOf("fallback"),
            missingBeatLabels = emptyList(),
            editingDirection = "fallback",
            selectionReasonByClipId = emptyMap(),
        )
    }
}
