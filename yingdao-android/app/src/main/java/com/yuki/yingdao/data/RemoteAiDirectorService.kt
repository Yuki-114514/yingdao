package com.yuki.yingdao.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val AI_CONNECT_TIMEOUT_SECONDS = 10L
internal const val AI_CALL_TIMEOUT_SECONDS = 120L
internal const val AI_READ_TIMEOUT_SECONDS = 120L
internal const val AI_WRITE_TIMEOUT_SECONDS = 120L

class RemoteAiDirectorService(
    private val baseUrl: String,
    private val appToken: String = "",
    private val client: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = Gson(),
) : AiDirectorService {
    override suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan> {
        return post(
            path = "/v1/ai/director-plan",
            payload = GenerateDirectorPlanRequest(brief = brief),
            typeToken = object : TypeToken<ApiEnvelope<DirectorPlan>>() {},
        )
    }

    override suspend fun reviewClip(shotTask: ShotTask, attemptNumber: Int): Result<ClipReview> {
        return post(
            path = "/v1/ai/clip-review",
            payload = ReviewClipRequest(
                shotTask = shotTask,
                attemptNumber = attemptNumber,
            ),
            typeToken = object : TypeToken<ApiEnvelope<ClipReview>>() {},
        )
    }

    override suspend fun buildAssembly(project: Project): Result<AssemblySuggestion> {
        return post(
            path = "/v1/ai/assembly-suggestion",
            payload = BuildAssemblyRequest(project = project),
            typeToken = object : TypeToken<ApiEnvelope<AssemblySuggestion>>() {},
        )
    }

    private suspend fun <T> post(
        path: String,
        payload: Any,
        typeToken: TypeToken<ApiEnvelope<T>>,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(resolveUrl(path))
                .post(body)
            if (appToken.isNotBlank()) {
                requestBuilder.header(APP_TOKEN_HEADER, appToken)
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val remoteError = parseError(rawBody)
                    return@withContext Result.failure(
                        IOException(remoteError ?: "AI 服务请求失败：HTTP ${response.code}"),
                    )
                }

                val envelope = gson.fromJson<ApiEnvelope<T>>(rawBody, typeToken.type)
                val data = envelope?.data
                if (envelope == null || !envelope.success || data == null) {
                    return@withContext Result.failure(
                        IOException(envelope?.error ?: "AI 服务响应缺少有效数据"),
                    )
                }
                Result.success(data)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun resolveUrl(path: String): String {
        return baseUrl.trimEnd('/') + path
    }

    private fun parseError(rawBody: String): String? {
        return runCatching {
            gson.fromJson(rawBody, ApiEnvelope::class.java)?.error as? String
        }.getOrNull()
    }

    private companion object {
        const val APP_TOKEN_HEADER = "X-YingDao-App-Token"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun defaultHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(AI_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(AI_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(AI_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(AI_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
