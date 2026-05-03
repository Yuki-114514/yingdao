package com.yuki.yingdao.data

data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T?,
    val error: String?,
)

enum class AiJobStatus {
    Pending,
    Succeeded,
    Failed,
}

data class StartAiJobResponse(
    val jobId: String,
    val status: AiJobStatus,
)

data class AiJobResult<T>(
    val jobId: String,
    val status: AiJobStatus,
    val data: T?,
    val error: String?,
)

data class GenerateDirectorPlanRequest(
    val brief: CreativeBrief,
)

data class ReviewClipRequest(
    val shotTask: ShotTask,
    val attemptNumber: Int,
    val mediaType: MediaType,
    val capturedMedia: CapturedMediaRequest? = null,
)

data class BuildAssemblyRequest(
    val project: Project,
)

data class CapturedMediaRequest(
    val mimeType: String,
    val dataBase64: String,
)
