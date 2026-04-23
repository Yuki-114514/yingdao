package com.yuki.yingdao.data

import com.yuki.yingdao.BuildConfig

object AppContainer {
    fun aiDirectorService(): AiDirectorService {
        val baseUrl = BuildConfig.AI_BASE_URL.trim()
        return if (baseUrl.isBlank() || baseUrl == "https://example.invalid/") {
            MisconfiguredAiDirectorService()
        } else {
            RemoteAiDirectorService(baseUrl = baseUrl)
        }
    }
}

private class MisconfiguredAiDirectorService : AiDirectorService {
    override suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan> {
        return Result.failure(IllegalStateException("AI 服务地址还没配置好。"))
    }

    override suspend fun reviewClip(shotTask: ShotTask, attemptNumber: Int): Result<ClipReview> {
        return Result.failure(IllegalStateException("AI 服务地址还没配置好。"))
    }

    override suspend fun buildAssembly(project: Project): Result<AssemblySuggestion> {
        return Result.failure(IllegalStateException("AI 服务地址还没配置好。"))
    }
}
