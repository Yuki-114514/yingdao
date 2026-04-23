package com.yuki.yingdao.data

data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T?,
    val error: String?,
)

data class GenerateDirectorPlanRequest(
    val brief: CreativeBrief,
)

data class ReviewClipRequest(
    val shotTask: ShotTask,
    val attemptNumber: Int,
)

data class BuildAssemblyRequest(
    val project: Project,
)
