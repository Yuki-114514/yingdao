package com.yuki.yingdao.data

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
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
    fun generateDirectorPlanPostsBriefAndParsesEnvelope() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "data": {
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
        val brief = CreativeBrief(
            title = "图书馆状态记录",
            theme = "安静自习日常",
            highlightSubject = "图书馆里的学习状态",
        )

        val result = service.generateDirectorPlan(brief)
        val request = server.takeRequest()

        assertTrue(result.isSuccess)
        assertEquals("/v1/ai/director-plan", request.path)
        assertTrue(request.body.readUtf8().contains("图书馆状态记录"))
        assertEquals("AI 校园短片方案", result.getOrThrow().title)

        server.shutdown()
    }

    @Test
    fun generateDirectorPlanSendsAppTokenWhenConfigured() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "data": {
                        "title": "AI 校园短片方案",
                        "storyLogline": "围绕图书馆里的学习状态展开的一天。",
                        "beatSummary": ["建立环境"],
                        "shotTasks": []
                      },
                      "error": null
                    }
                    """.trimIndent(),
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

        assertEquals("demo-token", request.getHeader("X-YingDao-App-Token"))

        server.shutdown()
    }

    @Test
    fun reviewClipPostsAttemptAndReturnsClipReview() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "data": {
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

        val result = service.reviewClip(shot, attemptNumber = 2)
        val request = server.takeRequest()

        assertTrue(result.isSuccess)
        assertEquals("/v1/ai/clip-review", request.path)
        assertTrue(request.body.readUtf8().contains("\"attemptNumber\":2"))
        assertEquals(89, result.getOrThrow().score)

        server.shutdown()
    }

    @Test
    fun buildAssemblyPostsProjectAndReturnsAssemblySuggestion() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "data": {
                        "orderedClipIds": ["clip_1"],
                        "missingShotIds": ["shot_02"],
                        "titleOptions": ["图书馆的一天"],
                        "captionDraft": ["把今天安静留下来。"],
                        "missingBeatLabels": ["结尾收束"],
                        "editingDirection": "先接开场，再补一个结尾。",
                        "selectionReasonByClipId": {
                          "clip_1": "它已经承担了开场建立。"
                        }
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
        val request = server.takeRequest()

        assertTrue(result.isSuccess)
        assertEquals("/v1/ai/assembly-suggestion", request.path)
        assertTrue(request.body.readUtf8().contains("测试项目"))
        assertEquals(listOf("clip_1"), result.getOrThrow().orderedClipIds)

        server.shutdown()
    }

    @Test
    fun generateDirectorPlanReturnsFailureWhenHttpStatusIsNotSuccessful() = runTest {
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
    fun buildAssemblyReturnsFailureWhenEnvelopeHasNoData() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "data": null, "error": null}"""),
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
