package com.yuki.yingdao.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuki.yingdao.data.AiDirectorService
import com.yuki.yingdao.data.AssemblySuggestion
import com.yuki.yingdao.data.ClipAsset
import com.yuki.yingdao.data.ClipReview
import com.yuki.yingdao.data.CreativeBrief
import com.yuki.yingdao.data.DirectorTemplates
import com.yuki.yingdao.data.FakeAiDirectorService
import com.yuki.yingdao.data.Project
import com.yuki.yingdao.data.ProjectStatus
import com.yuki.yingdao.data.ShotStatus
import com.yuki.yingdao.data.ShotTask
import com.yuki.yingdao.data.TemplatePreset
import com.yuki.yingdao.data.TimePressure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class YingDaoUiState(
    val templates: List<TemplatePreset> = DirectorTemplates.presets,
    val selectedTemplateId: String = DirectorTemplates.presets.first().id,
    val draft: CreativeBrief = CreativeBrief(),
    val projects: List<Project> = emptyList(),
    val activeProjectId: String? = null,
    val selectedShotId: String? = null,
    val isGeneratingPlan: Boolean = false,
    val isBuildingAssembly: Boolean = false,
    val isReviewingClip: Boolean = false,
    val planError: String? = null,
    val assemblyError: String? = null,
    val reviewError: String? = null,
) {
    val activeProject: Project?
        get() = projects.find { it.id == activeProjectId }

    val selectedShot: ShotTask?
        get() = activeProject
            ?.directorPlan
            ?.shotTasks
            ?.find { it.id == selectedShotId }
}

class YingDaoViewModel(
    private val aiDirectorService: AiDirectorService = FakeAiDirectorService(),
) : ViewModel() {
    private var projectCounter = 0
    private var clipCounter = 0

    var uiState by mutableStateOf(YingDaoUiState())
        private set

    fun updateDraft(
        title: String? = null,
        theme: String? = null,
        style: String? = null,
        durationSec: Int? = null,
        castCount: Int? = null,
        locations: List<String>? = null,
        needCaption: Boolean? = null,
        needVoiceover: Boolean? = null,
        shootGoal: String? = null,
        mood: String? = null,
        highlightSubject: String? = null,
        soloMode: Boolean? = null,
        timePressure: TimePressure? = null,
    ) {
        uiState = uiState.copy(
            draft = uiState.draft.copy(
                title = title ?: uiState.draft.title,
                theme = theme ?: uiState.draft.theme,
                style = style ?: uiState.draft.style,
                durationSec = durationSec ?: uiState.draft.durationSec,
                castCount = castCount ?: uiState.draft.castCount,
                locations = locations ?: uiState.draft.locations,
                needCaption = needCaption ?: uiState.draft.needCaption,
                needVoiceover = needVoiceover ?: uiState.draft.needVoiceover,
                shootGoal = shootGoal ?: uiState.draft.shootGoal,
                mood = mood ?: uiState.draft.mood,
                highlightSubject = highlightSubject ?: uiState.draft.highlightSubject,
                soloMode = soloMode ?: uiState.draft.soloMode,
                timePressure = timePressure ?: uiState.draft.timePressure,
            ),
        )
    }

    fun selectTemplate(templateId: String) {
        uiState = uiState.copy(selectedTemplateId = templateId)
    }

    fun resetDraft() {
        uiState = uiState.copy(
            draft = CreativeBrief(),
            selectedTemplateId = DirectorTemplates.presets.first().id,
            activeProjectId = null,
            selectedShotId = null,
            isGeneratingPlan = false,
            isBuildingAssembly = false,
            isReviewingClip = false,
            planError = null,
            assemblyError = null,
            reviewError = null,
        )
    }

    fun openProject(projectId: String) {
        val project = uiState.projects.find { it.id == projectId } ?: return
        val firstRelevantShotId = project.directorPlan
            ?.shotTasks
            ?.firstOrNull { it.status == ShotStatus.Planned || it.status == ShotStatus.RetakeSuggested }
            ?.id
            ?: project.directorPlan?.shotTasks?.firstOrNull()?.id

        uiState = uiState.copy(
            activeProjectId = projectId,
            selectedShotId = firstRelevantShotId,
        )
    }

    fun generateDirectorPlan() {
        val draft = uiState.draft
        val templateId = uiState.selectedTemplateId
        uiState = uiState.copy(
            isGeneratingPlan = true,
            planError = null,
        )
        viewModelScope.launch {
            try {
                aiDirectorService.generateDirectorPlan(draft)
                    .onSuccess { plan ->
                        val projectId = "proj_${projectCounter++}"
                        val project = Project(
                            id = projectId,
                            title = draft.title,
                            templateId = templateId,
                            status = ProjectStatus.ShotPlanReady,
                            brief = draft,
                            directorPlan = plan,
                            clips = emptyList(),
                        )
                        uiState = uiState.copy(
                            projects = listOf(project) + uiState.projects,
                            activeProjectId = projectId,
                            selectedShotId = plan.shotTasks.firstOrNull()?.id,
                            isGeneratingPlan = false,
                        )
                    }
                    .onFailure { error ->
                        uiState = uiState.copy(
                            isGeneratingPlan = false,
                            planError = error.message ?: "生成方案失败，请稍后再试。",
                        )
                    }
            } catch (error: CancellationException) {
                throw error
            }
        }
    }

    fun selectShot(shotId: String) {
        uiState = uiState.copy(selectedShotId = shotId)
    }

    fun simulateCaptureForSelectedShot() {
        val mockPath = "mock://clip_${clipCounter}.mp4"
        registerRecordedClipForSelectedShot(
            localPath = mockPath,
            durationSec = (uiState.selectedShot?.durationSuggestSec ?: 3).toDouble(),
        )
    }

    fun registerRecordedClipForSelectedShot(
        localPath: String,
        durationSec: Double,
    ) {
        val project = uiState.activeProject ?: return
        val shot = uiState.selectedShot ?: return
        val attempt = shot.capturedClipIds.size + 1
        val clipId = "clip_${clipCounter++}"
        val clip = ClipAsset(
            id = clipId,
            shotTaskId = shot.id,
            localPath = localPath,
            durationSec = durationSec,
            thumbnailLabel = shot.title,
        )

        uiState = uiState.copy(
            isReviewingClip = true,
            reviewError = null,
        )
        viewModelScope.launch {
            try {
                aiDirectorService.reviewClip(shot, attempt)
                    .map { review -> review.copy(clipId = clipId) }
                    .onSuccess { review ->
                        applyClipReview(projectId = project.id, shotId = shot.id, clip = clip, review = review)
                        uiState = uiState.copy(isReviewingClip = false)
                    }
                    .onFailure { error ->
                        applyClipReviewFailure(projectId = project.id, clip = clip)
                        uiState = uiState.copy(
                            isReviewingClip = false,
                            reviewError = error.message ?: "点评生成失败，请稍后再试。",
                        )
                    }
            } catch (error: CancellationException) {
                uiState = uiState.copy(isReviewingClip = false)
                throw error
            }
        }
    }

    fun approveSelectedShot() {
        val selectedShotId = uiState.selectedShotId ?: return
        var didApprove = false
        updateActiveProject { active ->
            val selectedShot = active.directorPlan?.shotTasks.orEmpty().firstOrNull { it.id == selectedShotId }
                ?: return@updateActiveProject active
            if (selectedShot.latestReview?.usable != true || selectedShot.capturedClipIds.isEmpty()) {
                return@updateActiveProject active
            }
            val updatedShots = active.directorPlan?.shotTasks.orEmpty().map { task ->
                if (task.id == selectedShotId) task.copy(status = ShotStatus.Approved) else task
            }
            val allResolved = updatedShots.all {
                it.status == ShotStatus.Approved || it.status == ShotStatus.Skipped
            }
            didApprove = true
            active.copy(
                status = if (allResolved) ProjectStatus.ReviewReady else ProjectStatus.Shooting,
                directorPlan = active.directorPlan?.copy(shotTasks = updatedShots),
            )
        }
        if (didApprove) {
            moveToNextUnresolvedShot()
        }
    }

    fun requestRetakeForSelectedShot() {
        val selectedShotId = uiState.selectedShotId ?: return
        updateActiveProject { active ->
            val updatedShots = active.directorPlan?.shotTasks.orEmpty().map { task ->
                if (task.id == selectedShotId) {
                    task.copy(
                        status = ShotStatus.Planned,
                        latestReview = null,
                    )
                } else {
                    task
                }
            }
            active.copy(
                status = ProjectStatus.Shooting,
                directorPlan = active.directorPlan?.copy(shotTasks = updatedShots),
            )
        }
    }

    fun skipSelectedShot() {
        val selectedShotId = uiState.selectedShotId ?: return
        updateActiveProject { active ->
            val updatedShots = active.directorPlan?.shotTasks.orEmpty().map { task ->
                if (task.id == selectedShotId) task.copy(status = ShotStatus.Skipped) else task
            }
            val allResolved = updatedShots.all {
                it.status == ShotStatus.Approved || it.status == ShotStatus.Skipped
            }
            active.copy(
                status = if (allResolved) ProjectStatus.ReviewReady else ProjectStatus.Shooting,
                directorPlan = active.directorPlan?.copy(shotTasks = updatedShots),
            )
        }
        moveToNextUnresolvedShot()
    }

    fun buildAssemblySuggestion() {
        val project = uiState.activeProject ?: return
        uiState = uiState.copy(
            isBuildingAssembly = true,
            assemblyError = null,
        )
        viewModelScope.launch {
            try {
                aiDirectorService.buildAssembly(project)
                    .onSuccess { suggestion ->
                        updateActiveProject { active ->
                            active.copy(
                                status = if (suggestion.missingShotIds.isEmpty() && suggestion.orderedClipIds.isNotEmpty()) {
                                    ProjectStatus.AssemblyReady
                                } else {
                                    active.status
                                },
                                assemblySuggestion = suggestion,
                            )
                        }
                        uiState = uiState.copy(isBuildingAssembly = false)
                    }
                    .onFailure { error ->
                        uiState = uiState.copy(
                            isBuildingAssembly = false,
                            assemblyError = error.message ?: "出片建议生成失败，请稍后再试。",
                        )
                    }
            } catch (error: CancellationException) {
                uiState = uiState.copy(isBuildingAssembly = false)
                throw error
            }
        }
    }

    private fun moveToNextUnresolvedShot() {
        val nextShotId = uiState.activeProject
            ?.directorPlan
            ?.shotTasks
            ?.firstOrNull { it.status == ShotStatus.Planned || it.status == ShotStatus.RetakeSuggested }
            ?.id
        if (nextShotId != null) {
            uiState = uiState.copy(selectedShotId = nextShotId)
        }
    }

    private fun applyClipReview(
        projectId: String,
        shotId: String,
        clip: ClipAsset,
        review: ClipReview,
    ) {
        val updatedProjects = uiState.projects.map { project ->
            if (project.id != projectId) return@map project
            val updatedShots = project.directorPlan?.shotTasks.orEmpty().map { task ->
                if (task.id == shotId) {
                    task.copy(
                        status = if (review.usable) ShotStatus.Captured else ShotStatus.RetakeSuggested,
                        capturedClipIds = task.capturedClipIds + clip.id,
                        latestReview = review,
                    )
                } else {
                    task
                }
            }
            project.copy(
                status = ProjectStatus.Shooting,
                directorPlan = project.directorPlan?.copy(shotTasks = updatedShots),
                clips = project.clips + clip.copy(review = review),
            )
        }
        uiState = uiState.copy(projects = updatedProjects)
    }

    private fun applyClipReviewFailure(
        projectId: String,
        clip: ClipAsset,
    ) {
        val updatedProjects = uiState.projects.map { project ->
            if (project.id != projectId) return@map project
            project.copy(clips = project.clips + clip)
        }
        uiState = uiState.copy(projects = updatedProjects)
    }

    private fun updateActiveProject(transform: (Project) -> Project) {
        val activeProjectId = uiState.activeProjectId ?: return
        val updatedProjects = uiState.projects.map { project ->
            if (project.id == activeProjectId) transform(project) else project
        }
        uiState = uiState.copy(projects = updatedProjects)
    }
}
