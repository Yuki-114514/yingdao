package com.yuki.yingdao.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val AI_CONNECT_TIMEOUT_SECONDS = 10L
internal const val AI_CALL_TIMEOUT_SECONDS = 240L
internal const val AI_READ_TIMEOUT_SECONDS = 240L
internal const val AI_WRITE_TIMEOUT_SECONDS = 240L
internal const val AI_JOB_MAX_WAIT_MS = 240_000L
internal const val AI_JOB_POLL_INTERVAL_MS = 3_000L

class RemoteAiDirectorService(
    private val baseUrl: String,
    private val appToken: String = "",
    private val client: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = Gson(),
    private val capturedMediaReader: CapturedMediaSource? = null,
    private val jobMaxWaitMs: Long = AI_JOB_MAX_WAIT_MS,
    private val jobPollIntervalMs: Long = AI_JOB_POLL_INTERVAL_MS,
) : AiDirectorService {
    override suspend fun generateDirectorPlan(brief: CreativeBrief): Result<DirectorPlan> {
        return postJob(
            startPath = "/v1/ai/jobs/director-plan",
            payload = GenerateDirectorPlanRequest(brief = brief),
            resultTypeToken = object : TypeToken<ApiEnvelope<AiJobResult<DirectorPlan>>>() {},
        )
    }

    override suspend fun reviewClip(
        shotTask: ShotTask,
        attemptNumber: Int,
        mediaType: MediaType,
        localPath: String?,
    ): Result<ClipReview> {
        val capturedMedia = if (mediaType == MediaType.Photo && localPath != null) {
            withContext(Dispatchers.IO) {
                runCatching { capturedMediaReader?.readPhoto(localPath) }.getOrNull()
            }
        } else {
            null
        }
        return postJob(
            startPath = "/v1/ai/jobs/clip-review",
            payload = ReviewClipRequest(
                shotTask = shotTask,
                attemptNumber = attemptNumber,
                mediaType = mediaType,
                capturedMedia = capturedMedia,
            ),
            resultTypeToken = object : TypeToken<ApiEnvelope<AiJobResult<ClipReview>>>() {},
        )
    }

    override suspend fun buildAssembly(project: Project): Result<AssemblySuggestion> {
        return postJob(
            startPath = "/v1/ai/jobs/assembly-suggestion",
            payload = BuildAssemblyRequest(project = project),
            resultTypeToken = object : TypeToken<ApiEnvelope<AiJobResult<AssemblySuggestion>>>() {},
        )
    }

    private suspend fun <T> postJob(
        startPath: String,
        payload: Any,
        resultTypeToken: TypeToken<ApiEnvelope<AiJobResult<T>>>,
    ): Result<T> {
        return try {
            val startResponse = withContext(Dispatchers.IO) {
                val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
                val request = newRequestBuilder(startPath)
                    .post(body)
                    .build()
                executeEnvelopeRequest(
                    request = request,
                    typeToken = object : TypeToken<ApiEnvelope<StartAiJobResponse>>() {},
                )
            }
            val result = withTimeout(jobMaxWaitMs) {
                pollJob(startResponse.jobId, resultTypeToken)
            }
            Result.success(result)
        } catch (error: TimeoutCancellationException) {
            Result.failure(IOException("AI 服务生成超时，请稍后重试。", error))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun <T> pollJob(
        jobId: String,
        typeToken: TypeToken<ApiEnvelope<AiJobResult<T>>>,
    ): T {
        while (true) {
            val job = withContext(Dispatchers.IO) {
                val request = newRequestBuilder("/v1/ai/jobs/$jobId")
                    .get()
                    .build()
                executeEnvelopeRequest(request, typeToken)
            }

            when (job.status) {
                AiJobStatus.Pending -> delay(jobPollIntervalMs)
                AiJobStatus.Succeeded -> {
                    return job.data ?: throw IOException("AI 服务响应缺少有效数据")
                }
                AiJobStatus.Failed -> {
                    throw IOException(job.error ?: "AI 服务生成失败，请稍后重试。")
                }
            }
        }
    }

    private fun <T> executeEnvelopeRequest(
        request: Request,
        typeToken: TypeToken<ApiEnvelope<T>>,
    ): T {
        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val remoteError = parseError(rawBody)
                throw IOException(remoteError ?: "AI 服务请求失败：HTTP ${response.code}")
            }

            val envelope = gson.fromJson<ApiEnvelope<T>>(rawBody, typeToken.type)
            val data = envelope?.data
            if (envelope == null || !envelope.success || data == null) {
                throw IOException(envelope?.error ?: "AI 服务响应缺少有效数据")
            }
            return data
        }
    }

    private fun newRequestBuilder(path: String): Request.Builder {
        val requestBuilder = Request.Builder()
            .url(resolveUrl(path))
        if (appToken.isNotBlank()) {
            requestBuilder.header(APP_TOKEN_HEADER, appToken)
        }
        return requestBuilder
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
