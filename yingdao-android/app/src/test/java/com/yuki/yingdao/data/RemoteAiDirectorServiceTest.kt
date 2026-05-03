package com.yuki.yingdao.data

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RemoteAiDirectorServiceTest {
    private val gson = Gson()

    @Test
    fun generateDirectorPlanPostsBriefAndParsesEnvelope() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_plan_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    completedJobEnvelope(
                        "job_plan_1",
                        """
                        {
                        "title": "AI 校园短片方案",
                        "storyLogline": "围绕图书馆里的学习状态展开的一天。",
                        "beatSummary": ["建立环境", "推进状态", "完成收尾"],
                        "shotTasks": [
                          {
                            "id": "shot_01",
                            "orderIndex": 1,
                            "title": "图书馆开场",
                            "goal": "先建立空间和人物状态",
                            "shotType": "中景",
                            "durationSuggestSec": 4,
                            "compositionHint": "人物保持在三分线附近",
                            "actionHint": "自然翻页",
                            "status": "Planned",
                            "capturedClipIds": [],
                            "latestReview": null,
                            "beatLabel": "开场建立",
                            "whyThisShotMatters": "让观众快速进入今天的氛围",
                            "successChecklist": ["人物清晰", "画面稳定"],
                            "difficultyHint": "先稳住镜头再录",
                            "retakePriority": "High"
                          }
                        ]
                      }
                        """.trimIndent(),
                    ),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
        )
        val brief = CreativeBrief(
            title = "图书馆状态记录",
            theme = "安静自习日常",
            highlightSubject = "图书馆里的学习状态",
        )

        val result = service.generateDirectorPlan(brief)
        val startRequest = server.takeRequest()
        val pollRequest = server.takeRequest(1, TimeUnit.SECONDS)

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals("/v1/ai/jobs/director-plan", startRequest.path)
        assertEquals("/v1/ai/jobs/job_plan_1", pollRequest?.path)
        assertTrue(startRequest.body.readUtf8().contains("图书馆状态记录"))
        assertEquals("AI 校园短片方案", result.getOrThrow().title)

        server.shutdown()
    }

    @Test
    fun generateDirectorPlanSendsAppTokenWhenConfigured() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_token_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    completedJobEnvelope(
                        "job_token_1",
                        """
                        {
                        "title": "AI 校园短片方案",
                        "storyLogline": "围绕图书馆里的学习状态展开的一天。",
                        "beatSummary": ["建立环境"],
                        "shotTasks": []
                      }
                        """.trimIndent(),
                    ),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            appToken = "demo-token",
            client = OkHttpClient(),
            gson = gson,
        )

        service.generateDirectorPlan(CreativeBrief())
        val request = server.takeRequest()
        val pollRequest = server.takeRequest(1, TimeUnit.SECONDS)

        assertEquals("demo-token", request.getHeader("X-YingDao-App-Token"))
        assertEquals("demo-token", pollRequest?.getHeader("X-YingDao-App-Token"))

        server.shutdown()
    }

    @Test
    fun generateDirectorPlanPollsUntilJobSucceeds() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_pending_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(pendingJobEnvelope("job_pending_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    completedJobEnvelope(
                        "job_pending_1",
                        """
                        {
                          "title": "AI 校园短片方案",
                          "storyLogline": "围绕图书馆里的学习状态展开的一天。",
                          "beatSummary": ["建立环境"],
                          "shotTasks": []
                        }
                        """.trimIndent(),
                    ),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
            jobPollIntervalMs = 1L,
        )

        val result = service.generateDirectorPlan(CreativeBrief())

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals("/v1/ai/jobs/director-plan", server.takeRequest().path)
        assertEquals("/v1/ai/jobs/job_pending_1", server.takeRequest(1, TimeUnit.SECONDS)?.path)
        assertEquals("/v1/ai/jobs/job_pending_1", server.takeRequest(1, TimeUnit.SECONDS)?.path)

        server.shutdown()
    }

    @Test
    fun reviewClipPostsAttemptAndReturnsClipReview() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_review_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    completedJobEnvelope(
                        "job_review_1",
                        """
                        {
                        "clipId": "",
                        "usable": true,
                        "score": 89,
                        "issues": ["这一条已经达到了当前镜头目标"],
                        "suggestion": "已经可用，继续推进下一个镜头。",
                        "stabilityScore": 88,
                        "subjectScore": 90,
                        "compositionScore": 87,
                        "emotionScore": 91,
                        "keepReason": "这条可以先保留。",
                        "retakeReason": "",
                        "nextAction": "继续推进下一个镜头。"
                      }
                        """.trimIndent(),
                    ),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
        )
        val shot = ShotTask(
            id = "shot_01",
            orderIndex = 1,
            title = "图书馆开场",
            goal = "先建立空间和人物状态",
            shotType = "中景",
            durationSuggestSec = 4,
            compositionHint = "人物保持在三分线附近",
            actionHint = "自然翻页",
        )

        val result = service.reviewClip(shot, attemptNumber = 2, mediaType = MediaType.Photo)
        val startRequest = server.takeRequest()
        val pollRequest = server.takeRequest(1, TimeUnit.SECONDS)
        val requestBody = startRequest.body.readUtf8()

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals("/v1/ai/jobs/clip-review", startRequest.path)
        assertEquals("/v1/ai/jobs/job_review_1", pollRequest?.path)
        assertTrue(requestBody.contains("\"attemptNumber\":2"))
        assertTrue(requestBody.contains("\"mediaType\":\"Photo\""))
        assertEquals(89, result.getOrThrow().score)

        server.shutdown()
    }

    @Test
    fun reviewClipIncludesCapturedPhotoWhenReaderReturnsMedia() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_review_photo_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    completedJobEnvelope(
                        "job_review_photo_1",
                        """
                        {
                        "clipId": "",
                        "usable": true,
                        "score": 90,
                        "issues": ["照片主体清楚"],
                        "suggestion": "这张可以保留。",
                        "stabilityScore": 90,
                        "subjectScore": 90,
                        "compositionScore": 90,
                        "emotionScore": 90,
                        "keepReason": "主体和环境都成立。",
                        "retakeReason": "",
                        "nextAction": "继续拍下一张。"
                      }
                        """.trimIndent(),
                    ),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
            capturedMediaReader = CapturedMediaSource { localPath ->
                assertEquals("content://media/external/images/media/9", localPath)
                CapturedMediaRequest(
                    mimeType = "image/jpeg",
                    dataBase64 = "abc123",
                )
            },
        )
        val shot = ShotTask(
            id = "shot_01",
            orderIndex = 1,
            title = "家里氛围照",
            goal = "拍清家里的光线和主体",
            shotType = "照片 / 环境",
            durationSuggestSec = 1,
            compositionHint = "画面留一点环境",
            actionHint = "站稳后拍一张",
        )

        val result = service.reviewClip(
            shotTask = shot,
            attemptNumber = 1,
            mediaType = MediaType.Photo,
            localPath = "content://media/external/images/media/9",
        )
        val requestBody = server.takeRequest().body.readUtf8()
        server.takeRequest(1, TimeUnit.SECONDS)

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertTrue(requestBody.contains("\"capturedMedia\""))
        assertTrue(requestBody.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(requestBody.contains("\"dataBase64\":\"abc123\""))

        server.shutdown()
    }

    @Test
    fun buildAssemblyPostsProjectAndReturnsAssemblySuggestion() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_assembly_1")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    completedJobEnvelope(
                        "job_assembly_1",
                        """
                        {
                        "orderedClipIds": ["clip_1"],
                        "missingShotIds": ["shot_02"],
                        "titleOptions": ["图书馆的一天"],
                        "captionDraft": ["把今天安静留下来。"],
                        "missingBeatLabels": ["结尾收束"],
                        "editingDirection": "先接开场，再补一个结尾。",
                        "selectionReasonByClipId": {
                          "clip_1": "它已经承担了开场建立。"
                        }
                      }
                        """.trimIndent(),
                    ),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
        )
        val project = Project(
            id = "proj_1",
            title = "测试项目",
            templateId = "campus_life",
            status = ProjectStatus.ReviewReady,
            brief = CreativeBrief(),
            directorPlan = DirectorPlan(
                title = "测试方案",
                storyLogline = "测试",
                beatSummary = listOf("建立环境"),
                shotTasks = listOf(
                    ShotTask(
                        id = "shot_01",
                        orderIndex = 1,
                        title = "开场",
                        goal = "建立环境",
                        shotType = "中景",
                        durationSuggestSec = 4,
                        compositionHint = "主体清晰",
                        actionHint = "自然进入画面",
                        status = ShotStatus.Approved,
                        capturedClipIds = listOf("clip_1"),
                    ),
                ),
            ),
            clips = listOf(
                ClipAsset(
                    id = "clip_1",
                    shotTaskId = "shot_01",
                    localPath = "content://clip/1",
                    durationSec = 3.4,
                    thumbnailLabel = "开场",
                ),
            ),
        )

        val result = service.buildAssembly(project)
        val startRequest = server.takeRequest()
        val pollRequest = server.takeRequest(1, TimeUnit.SECONDS)

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals("/v1/ai/jobs/assembly-suggestion", startRequest.path)
        assertEquals("/v1/ai/jobs/job_assembly_1", pollRequest?.path)
        assertTrue(startRequest.body.readUtf8().contains("测试项目"))
        assertEquals(listOf("clip_1"), result.getOrThrow().orderedClipIds)

        server.shutdown()
    }

    @Test
    fun generateDirectorPlanReturnsFailureWhenHttpStatusIsNotSuccessful() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("""{"success": false, "error": "upstream unavailable"}"""),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
        )

        val result = service.generateDirectorPlan(CreativeBrief())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("upstream unavailable") == true)

        server.shutdown()
    }

    @Test
    fun buildAssemblyReturnsFailureWhenEnvelopeHasNoData() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody(startJobEnvelope("job_missing_data")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "data": {
                        "jobId": "job_missing_data",
                        "status": "Succeeded",
                        "data": null,
                        "error": null
                      },
                      "error": null
                    }
                    """.trimIndent(),
                ),
        )
        server.start()

        val service = RemoteAiDirectorService(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            gson = gson,
        )
        val project = Project(
            id = "proj_1",
            title = "测试项目",
            templateId = "campus_life",
            status = ProjectStatus.ReviewReady,
            brief = CreativeBrief(),
            directorPlan = null,
            clips = emptyList(),
        )

        val result = service.buildAssembly(project)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("响应") == true)

        server.shutdown()
    }

    @Test
    fun defaultHttpClientUsesExtendedAiTimeouts() {
        val client = defaultHttpClient()

        assertEquals(AI_CONNECT_TIMEOUT_SECONDS, client.connectTimeoutMillis.toLong() / TimeUnit.SECONDS.toMillis(1))
        assertEquals(AI_CALL_TIMEOUT_SECONDS, client.callTimeoutMillis.toLong() / TimeUnit.SECONDS.toMillis(1))
        assertEquals(AI_READ_TIMEOUT_SECONDS, client.readTimeoutMillis.toLong() / TimeUnit.SECONDS.toMillis(1))
        assertEquals(AI_WRITE_TIMEOUT_SECONDS, client.writeTimeoutMillis.toLong() / TimeUnit.SECONDS.toMillis(1))
    }
}

private fun startJobEnvelope(jobId: String): String {
    return """
    {
      "success": true,
      "data": {
        "jobId": "$jobId",
        "status": "Pending"
      },
      "error": null
    }
    """.trimIndent()
}

private fun pendingJobEnvelope(jobId: String): String {
    return """
    {
      "success": true,
      "data": {
        "jobId": "$jobId",
        "status": "Pending",
        "data": null,
        "error": null
      },
      "error": null
    }
    """.trimIndent()
}

private fun completedJobEnvelope(jobId: String, dataJson: String): String {
    return """
    {
      "success": true,
      "data": {
        "jobId": "$jobId",
        "status": "Succeeded",
        "data": $dataJson,
        "error": null
      },
      "error": null
    }
    """.trimIndent()
}
