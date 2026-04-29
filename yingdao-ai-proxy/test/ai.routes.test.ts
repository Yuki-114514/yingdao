import { afterEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { readEnv } from '../src/config/env.js';
import {
  UpstreamAiError,
  type AiDirectorService,
  type AssemblySuggestion,
  type ClipReview,
  type DirectorPlan,
} from '../src/services/aiDirectorService.js';

const sampleDirectorPlan: DirectorPlan = {
  title: 'AI 校园短片方案',
  storyLogline: '围绕图书馆里的学习状态展开的一天。',
  beatSummary: ['建立环境', '推进状态', '完成收尾'],
  shotTasks: [
    {
      id: 'shot_01',
      orderIndex: 1,
      title: '图书馆开场',
      goal: '先建立空间和人物状态',
      shotType: '中景',
      durationSuggestSec: 4,
      compositionHint: '人物保持在三分线附近',
      actionHint: '自然翻页',
      status: 'Planned',
      capturedClipIds: [],
      latestReview: null,
      beatLabel: '开场建立',
      whyThisShotMatters: '让观众快速进入今天的氛围',
      successChecklist: ['人物清晰', '画面稳定'],
      difficultyHint: '先稳住镜头再录',
      retakePriority: 'High',
    },
  ],
};

const sampleClipReview: ClipReview = {
  clipId: 'clip_1',
  usable: true,
  score: 89,
  issues: ['这一条已经达到了当前镜头目标'],
  suggestion: '已经可用，继续推进下一个镜头。',
  stabilityScore: 88,
  subjectScore: 90,
  compositionScore: 87,
  emotionScore: 91,
  keepReason: '这条可以先保留。',
  retakeReason: '',
  nextAction: '继续推进下一个镜头。',
};

const sampleAssemblySuggestion: AssemblySuggestion = {
  orderedClipIds: ['clip_1'],
  missingShotIds: ['shot_02'],
  titleOptions: ['图书馆的一天'],
  captionDraft: ['把今天安静留下来。'],
  missingBeatLabels: ['结尾收束'],
  editingDirection: '先接开场，再补一个结尾。',
  selectionReasonByClipId: {
    clip_1: '它已经承担了开场建立。',
  },
};

const sampleBrief = {
  title: '图书馆状态记录',
  theme: '安静自习日常',
  style: '清新温暖',
  durationSec: 60,
  castCount: 1,
  locations: ['图书馆'],
  needCaption: true,
  needVoiceover: false,
  shootGoal: '拍出一条今天就能分享的校园短片',
  mood: '温和',
  highlightSubject: '图书馆里的学习状态',
  soloMode: false,
  timePressure: 'Medium',
};

const sampleShotTask = {
  id: 'shot_01',
  orderIndex: 1,
  title: '图书馆开场',
  goal: '先建立空间和人物状态',
  shotType: '中景',
  durationSuggestSec: 4,
  compositionHint: '人物保持在三分线附近',
  actionHint: '自然翻页',
  status: 'Planned',
  capturedClipIds: [],
  latestReview: null,
  beatLabel: '开场建立',
  whyThisShotMatters: '让观众快速进入今天的氛围',
  successChecklist: ['人物清晰', '画面稳定'],
  difficultyHint: '先稳住镜头再录',
  retakePriority: 'High',
};

const sampleProject = {
  id: 'proj_1',
  title: '测试项目',
  templateId: 'campus_life',
  status: 'ReviewReady',
  brief: sampleBrief,
  directorPlan: sampleDirectorPlan,
  clips: [
    {
      id: 'clip_1',
      shotTaskId: 'shot_01',
      localPath: 'content://clip/1',
      durationSec: 0,
      thumbnailLabel: '开场',
      mediaType: 'Photo',
      review: sampleClipReview,
    },
  ],
  assemblySuggestion: null,
};

function createService(overrides?: Partial<AiDirectorService>): AiDirectorService {
  return {
    async generateDirectorPlan() {
      return sampleDirectorPlan;
    },
    async reviewClip() {
      return sampleClipReview;
    },
    async buildAssembly() {
      return sampleAssemblySuggestion;
    },
    ...overrides,
  };
}

describe('AI routes', () => {
  let app: FastifyInstance | undefined;

  afterEach(async () => {
    await app?.close();
    app = undefined;
  });

  it('returns public health status for cloud health checks', async () => {
    app = buildApp({
      aiDirectorService: createService(),
      appToken: 'demo-token',
      rateLimit: {
        maxRequests: 1,
        windowMs: 60000,
      },
    });

    const response = await app.inject({
      method: 'GET',
      url: '/health',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      success: true,
      data: {
        status: 'ok',
      },
      error: null,
    });
  });

  it('returns director plan envelope for valid request', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        title: 'AI 校园短片方案',
      },
    });
  });

  it('starts async director plan job and exposes the completed result by job id', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const startResponse = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(startResponse.statusCode).toBe(202);
    expect(startResponse.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        status: 'Pending',
      },
    });

    const { jobId } = startResponse.json().data as { jobId: string };
    await new Promise((resolve) => setImmediate(resolve));

    const pollResponse = await app.inject({
      method: 'GET',
      url: `/v1/ai/jobs/${jobId}`,
    });

    expect(pollResponse.statusCode).toBe(200);
    expect(pollResponse.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        jobId,
        status: 'Succeeded',
        error: null,
        data: {
          title: 'AI 校园短片方案',
        },
      },
    });
  });

  it('starts async clip review job and exposes the completed result by job id', async () => {
    let capturedMediaType = '';
    let capturedMediaBase64 = '';
    app = buildApp({
      aiDirectorService: createService({
        async reviewClip(_shotTask, _attemptNumber, mediaType, capturedMedia) {
          capturedMediaType = mediaType ?? '';
          capturedMediaBase64 = capturedMedia?.dataBase64 ?? '';
          return sampleClipReview;
        },
      }),
    });

    const startResponse = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/clip-review',
      payload: {
        shotTask: sampleShotTask,
        attemptNumber: 2,
        mediaType: 'Photo',
        capturedMedia: {
          mimeType: 'image/jpeg',
          dataBase64: 'abc123',
        },
      },
    });

    expect(startResponse.statusCode).toBe(202);
    const { jobId } = startResponse.json().data as { jobId: string };
    await new Promise((resolve) => setImmediate(resolve));

    const pollResponse = await app.inject({
      method: 'GET',
      url: `/v1/ai/jobs/${jobId}`,
    });

    expect(capturedMediaType).toBe('Photo');
    expect(capturedMediaBase64).toBe('abc123');
    expect(pollResponse.statusCode).toBe(200);
    expect(pollResponse.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        jobId,
        status: 'Succeeded',
        error: null,
        data: {
          usable: true,
          score: 89,
        },
      },
    });
  });

  it('accepts async clip review requests when Android omits null latestReview', async () => {
    let capturedShotLatestReview: unknown = 'not-seen';
    app = buildApp({
      aiDirectorService: createService({
        async reviewClip(shotTask) {
          capturedShotLatestReview = shotTask.latestReview;
          return sampleClipReview;
        },
      }),
    });

    const { latestReview: _latestReview, ...shotTaskWithoutLatestReview } = sampleShotTask;
    const startResponse = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/clip-review',
      payload: { shotTask: shotTaskWithoutLatestReview, attemptNumber: 1, mediaType: 'Photo' },
    });

    expect(startResponse.statusCode).toBe(202);
    const { jobId } = startResponse.json().data as { jobId: string };
    await new Promise((resolve) => setImmediate(resolve));

    const pollResponse = await app.inject({
      method: 'GET',
      url: `/v1/ai/jobs/${jobId}`,
    });

    expect(capturedShotLatestReview).toBeNull();
    expect(pollResponse.json()).toMatchObject({
      data: {
        status: 'Succeeded',
        data: {
          score: 89,
        },
      },
    });
  });

  it('starts async assembly suggestion job and exposes the completed result by job id', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const startResponse = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/assembly-suggestion',
      payload: { project: sampleProject },
    });

    expect(startResponse.statusCode).toBe(202);
    const { jobId } = startResponse.json().data as { jobId: string };
    await new Promise((resolve) => setImmediate(resolve));

    const pollResponse = await app.inject({
      method: 'GET',
      url: `/v1/ai/jobs/${jobId}`,
    });

    expect(pollResponse.statusCode).toBe(200);
    expect(pollResponse.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        jobId,
        status: 'Succeeded',
        error: null,
        data: {
          orderedClipIds: ['clip_1'],
        },
      },
    });
  });

  it('accepts async assembly requests when Android omits nullable fields', async () => {
    let capturedProjectAssemblySuggestion: unknown = 'not-seen';
    let capturedClipReview: unknown = 'not-seen';
    app = buildApp({
      aiDirectorService: createService({
        async buildAssembly(project) {
          capturedProjectAssemblySuggestion = project.assemblySuggestion;
          capturedClipReview = project.clips[0]?.review;
          return sampleAssemblySuggestion;
        },
      }),
    });

    const { assemblySuggestion: _assemblySuggestion, ...projectWithoutAssemblySuggestion } = sampleProject;
    const { review: _review, ...clipWithoutReview } = sampleProject.clips[0];
    const { latestReview: _shotLatestReview, ...shotWithoutLatestReview } = sampleProject.directorPlan.shotTasks[0];
    const startResponse = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/assembly-suggestion',
      payload: {
        project: {
          ...projectWithoutAssemblySuggestion,
          directorPlan: {
            ...sampleProject.directorPlan,
            shotTasks: [shotWithoutLatestReview],
          },
          clips: [clipWithoutReview],
        },
      },
    });

    expect(startResponse.statusCode).toBe(202);
    const { jobId } = startResponse.json().data as { jobId: string };
    await new Promise((resolve) => setImmediate(resolve));

    const pollResponse = await app.inject({
      method: 'GET',
      url: `/v1/ai/jobs/${jobId}`,
    });

    expect(capturedProjectAssemblySuggestion).toBeNull();
    expect(capturedClipReview).toBeNull();
    expect(pollResponse.json()).toMatchObject({
      data: {
        status: 'Succeeded',
        data: {
          orderedClipIds: ['clip_1'],
        },
      },
    });
  });

  it('keeps async AI job failures pollable without holding the submit connection open', async () => {
    app = buildApp({
      aiDirectorService: createService({
        async generateDirectorPlan() {
          throw new UpstreamAiError('upstream unavailable');
        },
      }),
    });

    const startResponse = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(startResponse.statusCode).toBe(202);
    const { jobId } = startResponse.json().data as { jobId: string };
    await new Promise((resolve) => setImmediate(resolve));

    const pollResponse = await app.inject({
      method: 'GET',
      url: `/v1/ai/jobs/${jobId}`,
    });

    expect(pollResponse.statusCode).toBe(200);
    expect(pollResponse.json()).toEqual({
      success: true,
      error: null,
      data: {
        jobId,
        status: 'Failed',
        data: null,
        error: 'AI 服务暂时不可用，请稍后重试。',
      },
    });
  });

  it('returns 404 envelope for an unknown async AI job id', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'GET',
      url: '/v1/ai/jobs/missing-job',
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '任务不存在或已过期。',
    });
  });

  it('returns 400 envelope for invalid async director job payload', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/jobs/director-plan',
      payload: { brief: { title: 'only title' } },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求参数不合法。',
    });
  });

  it('accepts photo media type in director plan requests', async () => {
    let capturedMediaType = '';
    app = buildApp({
      aiDirectorService: createService({
        async generateDirectorPlan(brief) {
          capturedMediaType = brief.mediaType ?? '';
          return sampleDirectorPlan;
        },
      }),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: { ...sampleBrief, mediaType: 'Photo' } },
    });

    expect(response.statusCode).toBe(200);
    expect(capturedMediaType).toBe('Photo');
  });

  it('returns clip review envelope for valid request', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/clip-review',
      payload: { shotTask: sampleShotTask, attemptNumber: 2 },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        score: 89,
        usable: true,
      },
    });
  });

  it('passes photo media type to clip review service', async () => {
    let capturedMediaType = '';
    app = buildApp({
      aiDirectorService: createService({
        async reviewClip(_shotTask, _attemptNumber, mediaType) {
          capturedMediaType = mediaType ?? '';
          return sampleClipReview;
        },
      }),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/clip-review',
      payload: { shotTask: sampleShotTask, attemptNumber: 2, mediaType: 'Photo' },
    });

    expect(response.statusCode).toBe(200);
    expect(capturedMediaType).toBe('Photo');
  });

  it('returns 400 envelope for invalid clip review media type', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/clip-review',
      payload: { shotTask: sampleShotTask, attemptNumber: 2, mediaType: 'Audio' },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求参数不合法。',
    });
  });

  it('returns assembly suggestion envelope for valid request', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/assembly-suggestion',
      payload: { project: sampleProject },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        orderedClipIds: ['clip_1'],
      },
    });
  });

  it('rejects zero-duration assembly clips unless they are photos', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/assembly-suggestion',
      payload: {
        project: {
          ...sampleProject,
          clips: [
            {
              ...sampleProject.clips[0],
              mediaType: 'Video',
              durationSec: 0,
            },
          ],
        },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求参数不合法。',
    });
  });

  it('treats omitted assembly clip media type as video duration validation', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const { mediaType: _mediaType, ...clipWithoutMediaType } = sampleProject.clips[0];
    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/assembly-suggestion',
      payload: {
        project: {
          ...sampleProject,
          clips: [clipWithoutMediaType],
        },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求参数不合法。',
    });
  });

  it('returns 400 envelope when request body is invalid', async () => {
    app = buildApp({ aiDirectorService: createService() });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: { title: 'only title' } },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求参数不合法。',
    });
  });

  it('returns 502 envelope when upstream provider fails', async () => {
    app = buildApp({
      aiDirectorService: createService({
        async generateDirectorPlan() {
          throw new UpstreamAiError('upstream unavailable');
        },
      }),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(response.statusCode).toBe(502);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: 'AI 服务暂时不可用，请稍后重试。',
    });
  });

  it('returns 502 envelope when provider output is structurally invalid', async () => {
    app = buildApp({
      aiDirectorService: createService({
        async generateDirectorPlan() {
          return {
            title: 'AI 校园短片方案',
          } as DirectorPlan;
        },
      }),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(response.statusCode).toBe(502);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: 'AI 返回的数据格式不正确。',
    });
  });

  it('returns 401 envelope when configured app token is missing', async () => {
    app = buildApp({
      aiDirectorService: createService(),
      appToken: 'demo-token',
    });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(response.statusCode).toBe(401);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求未授权。',
    });
  });

  it('accepts request with configured app token', async () => {
    app = buildApp({
      aiDirectorService: createService(),
      appToken: 'demo-token',
    });

    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      headers: {
        'x-yingdao-app-token': 'demo-token',
      },
      payload: { brief: sampleBrief },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: true,
      error: null,
      data: {
        title: 'AI 校园短片方案',
      },
    });
  });

  it('returns 429 envelope after too many requests from one client', async () => {
    app = buildApp({
      aiDirectorService: createService(),
      rateLimit: {
        maxRequests: 1,
        windowMs: 60000,
      },
    });

    await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: sampleBrief },
    });
    const response = await app.inject({
      method: 'POST',
      url: '/v1/ai/director-plan',
      payload: { brief: sampleBrief },
    });

    expect(response.statusCode).toBe(429);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: '请求过于频繁，请稍后再试。',
    });
  });

  it('keeps localhost default host and accepts NVIDIA DeepSeek config', () => {
    const env = readEnv({
      PORT: '8787',
      MODEL_PROVIDER: 'openai-compatible',
      MODEL_BASE_URL: 'https://integrate.api.nvidia.com',
      MODEL_API_KEY: 'test-key',
      MODEL_NAME: 'deepseek-ai/deepseek-v4-pro',
      MODEL_FALLBACK_NAMES: 'meta/llama-3.1-8b-instruct',
      MODEL_JSON_RESPONSE_FORMAT: 'false',
      MODEL_ATTEMPT_TIMEOUT_MS: '45000',
      REQUEST_TIMEOUT_MS: '240000',
    });

    expect(env.HOST).toBe('127.0.0.1');
    expect(env.MODEL_BASE_URL).toBe('https://integrate.api.nvidia.com');
    expect(env.MODEL_NAME).toBe('deepseek-ai/deepseek-v4-pro');
    expect(env.MODEL_FALLBACK_NAMES).toEqual(['meta/llama-3.1-8b-instruct']);
    expect(env.MODEL_JSON_RESPONSE_FORMAT).toBe(false);
    expect(env.MODEL_ATTEMPT_TIMEOUT_MS).toBe(45000);
    expect(env.REQUEST_TIMEOUT_MS).toBe(240000);
  });

  it('requires app token for cloud host deployments', () => {
    expect(() =>
      readEnv({
        HOST: '0.0.0.0',
        PORT: '8787',
        MODEL_PROVIDER: 'openai-compatible',
        MODEL_BASE_URL: 'https://integrate.api.nvidia.com',
        MODEL_API_KEY: 'test-key',
        MODEL_NAME: 'deepseek-ai/deepseek-v4-pro',
        MODEL_FALLBACK_NAMES: 'meta/llama-3.1-8b-instruct',
        MODEL_JSON_RESPONSE_FORMAT: 'false',
        MODEL_ATTEMPT_TIMEOUT_MS: '45000',
        REQUEST_TIMEOUT_MS: '240000',
      }),
    ).toThrow();
  });
});
