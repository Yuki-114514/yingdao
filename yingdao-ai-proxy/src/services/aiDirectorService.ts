import { z } from 'zod';
import {
  assemblySuggestionSchema,
  type AssemblySuggestion,
  clipReviewSchema,
  type ClipReview,
  type CreativeBrief,
  directorPlanSchema,
  type DirectorPlan,
  type Project,
  shotStatusSchema,
  type ShotTask,
  type ReviewClipRequest,
} from '../schemas/ai.js';
import type { ProviderClient } from './providerClient.js';

const alternateDirectorPlanSchema = z.object({
  title: z.string(),
  summary: z
    .object({
      shootGoal: z.string().optional(),
    })
    .partial()
    .optional(),
  creativeDirection: z
    .object({
      coreIdea: z.string().optional(),
    })
    .partial()
    .optional(),
  timelineStructure: z
    .array(
      z.object({
        section: z.string(),
      }),
    )
    .optional(),
  shotList: z.array(
    z.object({
      id: z.union([z.string(), z.number().int()]),
      durationSec: z.number().int().positive().optional(),
      shotType: z.string().optional(),
      content: z.string().optional(),
      subjectAction: z.string().optional(),
      scene: z.string().optional(),
      caption: z.string().optional(),
    }),
  ),
});

const DIRECTOR_PLAN_MAX_TOKENS = 1400;
const CLIP_REVIEW_MAX_TOKENS = 450;
const ASSEMBLY_MAX_TOKENS = 900;

export interface AiDirectorService {
  generateDirectorPlan(brief: CreativeBrief): Promise<DirectorPlan>;
  reviewClip(
    shotTask: ShotTask,
    attemptNumber: number,
    mediaType?: ReviewClipRequest['mediaType'],
  ): Promise<ClipReview>;
  buildAssembly(project: Project): Promise<AssemblySuggestion>;
}

export class InvalidAiOutputError extends Error {}
export class UpstreamAiError extends Error {}

export class DefaultAiDirectorService implements AiDirectorService {
  constructor(private readonly providerClient: ProviderClient) {}

  async generateDirectorPlan(brief: CreativeBrief): Promise<DirectorPlan> {
    return this.generateStructured({
      schema: directorPlanSchema,
      systemPrompt:
        '你是日常影像拍摄助手，覆盖日常、美食、旅行、宠物、穿搭、学习、朋友聚会等主题，不局限校园。只返回 JSON 对象。字段只能有：title、storyLogline、beatSummary、shotTasks。beatSummary 必须是字符串数组。shotTasks 每项只能有：id、orderIndex、title、goal、shotType、durationSuggestSec、compositionHint、actionHint、status、capturedClipIds、latestReview、beatLabel、whyThisShotMatters、successChecklist、difficultyHint、retakePriority。latestReview 必须为 null。status 只能是 Planned、Active、Captured、Approved、RetakeSuggested、Skipped。retakePriority 只能是 Low、Medium、High。镜头数不超过 5。mediaType 为 Photo 时输出拍照任务，避免录制、运镜、收音建议；mediaType 为 Video 时输出视频镜头任务。每个字段尽量简洁。',
      userPrompt: JSON.stringify({ task: 'generate_director_plan', brief }),
      maxTokens: DIRECTOR_PLAN_MAX_TOKENS,
    });
  }

  async reviewClip(
    shotTask: ShotTask,
    attemptNumber: number,
    mediaType?: ReviewClipRequest['mediaType'],
  ): Promise<ClipReview> {
    const resolvedMediaType = mediaType ?? 'Video';
    return this.generateStructured({
      schema: clipReviewSchema,
      systemPrompt:
        '你是日常影像拍后点评助手。你看不到真实媒体，只能根据 shotTask、attemptNumber、mediaType 给出可执行点评。只返回 JSON 对象，字段只能有：clipId、usable、score、issues、suggestion、stabilityScore、subjectScore、compositionScore、emotionScore、keepReason、retakeReason、nextAction。分数必须是 0 到 100 的整数。Photo 用照片表达，Video 用视频表达。内容要具体、简洁。',
      userPrompt: JSON.stringify({
        task: 'review_clip',
        shotTask,
        attemptNumber,
        mediaType: resolvedMediaType,
      }),
      maxTokens: CLIP_REVIEW_MAX_TOKENS,
    });
  }

  async buildAssembly(project: Project): Promise<AssemblySuggestion> {
    return this.generateStructured({
      schema: assemblySuggestionSchema,
      systemPrompt:
        '你是日常影像整理助手。根据 project 的 brief、shotTasks、clips 和 review 生成出片建议。只返回 JSON 对象，字段只能有：orderedClipIds、missingShotIds、titleOptions、captionDraft、missingBeatLabels、editingDirection、selectionReasonByClipId。selectionReasonByClipId 的键必须是 clipId。内容简洁，不要 markdown。',
      userPrompt: JSON.stringify({ task: 'build_assembly', project }),
      maxTokens: ASSEMBLY_MAX_TOKENS,
    });
  }

  private async generateStructured<T>(input: {
    schema: z.ZodSchema<T>;
    systemPrompt: string;
    userPrompt: string;
    timeoutMs?: number;
    maxTokens?: number;
  }): Promise<T> {
    try {
      const output = await this.providerClient.generateObject({
        systemPrompt: input.systemPrompt,
        userPrompt: input.userPrompt,
        schema: z.unknown() as z.ZodSchema<T>,
        timeoutMs: input.timeoutMs,
        maxTokens: input.maxTokens,
      });
      return this.normalizeOutput(input.schema, output);
    } catch (error) {
      if (error instanceof SyntaxError) {
        throw new InvalidAiOutputError('AI 返回的数据格式不正确。');
      }
      if (error instanceof Error && error.name === 'ZodError') {
        throw new InvalidAiOutputError('AI 返回的数据格式不正确。');
      }
      if (error instanceof InvalidAiOutputError) {
        throw error;
      }
      if (error instanceof Error) {
        throw new UpstreamAiError(error.message);
      }
      throw new UpstreamAiError('AI 服务调用失败。');
    }
  }

  private normalizeOutput<T>(schema: z.ZodSchema<T>, output: unknown): T {
    if ((schema as unknown) === directorPlanSchema) {
      const normalizedDirectorPlan = this.normalizeDirectorPlanOutput(output);
      const normalizedResult = directorPlanSchema.safeParse(normalizedDirectorPlan);
      if (normalizedResult.success) {
        return normalizedResult.data as T;
      }
      throw normalizedResult.error;
    }

    if ((schema as unknown) === assemblySuggestionSchema) {
      const normalizedAssemblySuggestion = this.normalizeAssemblySuggestionOutput(output);
      const normalizedResult = assemblySuggestionSchema.safeParse(normalizedAssemblySuggestion);
      if (normalizedResult.success) {
        return normalizedResult.data as T;
      }
      throw normalizedResult.error;
    }

    if ((schema as unknown) === clipReviewSchema) {
      const normalizedClipReview = this.normalizeClipReviewOutput(output);
      const normalizedResult = clipReviewSchema.safeParse(normalizedClipReview);
      if (normalizedResult.success) {
        return normalizedResult.data as T;
      }
      throw normalizedResult.error;
    }

    const directResult = schema.safeParse(output);
    if (directResult.success) {
      return directResult.data;
    }

    throw directResult.error;
  }

  private normalizeDirectorPlanOutput(output: unknown): unknown {
    if (typeof output === 'object' && output !== null) {
      const candidate = output as {
        beatSummary?: unknown;
        shotTasks?: unknown;
      };
      const normalizedBeatSummary = this.normalizeBeatSummary(candidate.beatSummary);
      const normalizedShotTasks = this.normalizeShotTasks(candidate.shotTasks);
      if (normalizedBeatSummary !== candidate.beatSummary || normalizedShotTasks !== candidate.shotTasks) {
        return {
          ...candidate,
          beatSummary: normalizedBeatSummary,
          shotTasks: normalizedShotTasks,
        };
      }
    }

    const alternateResult = alternateDirectorPlanSchema.safeParse(output);
    if (alternateResult.success) {
      return this.mapAlternateDirectorPlan(alternateResult.data);
    }

    return output;
  }

  private normalizeBeatSummary(value: unknown): unknown {
    if (Array.isArray(value)) {
      return value;
    }

    if (typeof value !== 'string') {
      return value;
    }

    return value
      .split(/[→、，,。；;|/\n]+/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
      .slice(0, 8);
  }

  private normalizeShotTasks(value: unknown): unknown {
    if (!Array.isArray(value)) {
      return value;
    }

    return value.map((item) => {
      if (typeof item !== 'object' || item === null) {
        return item;
      }

      const candidate = item as {
        id?: unknown;
        capturedClipIds?: unknown;
        latestReview?: unknown;
        status?: unknown;
        retakePriority?: unknown;
      };
      const normalized = { ...candidate };

      if (typeof candidate.id === 'number') {
        normalized.id = `shot_${candidate.id}`;
      }

      if (!Array.isArray(candidate.capturedClipIds)) {
        normalized.capturedClipIds = [];
      }

      if (candidate.latestReview === undefined || typeof candidate.latestReview === 'string') {
        normalized.latestReview = null;
      }

      if (candidate.status === undefined) {
        normalized.status = 'Planned';
      }

      if (candidate.retakePriority === undefined) {
        normalized.retakePriority = 'Medium';
      }

      return normalized;
    });
  }

  private normalizeAssemblySuggestionOutput(output: unknown): unknown {
    if (typeof output !== 'object' || output === null) {
      return output;
    }

    const candidate = output as {
      captionDraft?: unknown;
    };

    if (typeof candidate.captionDraft !== 'string') {
      return candidate;
    }

    return {
      ...candidate,
      captionDraft: [candidate.captionDraft],
    };
  }

  private normalizeClipReviewOutput(output: unknown): unknown {
    if (typeof output !== 'object' || output === null) {
      return output;
    }

    const normalized: Record<string, unknown> = { ...(output as Record<string, unknown>) };
    for (const key of ['clipId', 'suggestion', 'keepReason', 'retakeReason', 'nextAction']) {
      if (normalized[key] === null || normalized[key] === undefined) {
        normalized[key] = '';
      }
    }

    if (typeof normalized.issues === 'string') {
      normalized.issues = [normalized.issues];
    }

    return normalized;
  }

  private mapAlternateDirectorPlan(output: z.infer<typeof alternateDirectorPlanSchema>): DirectorPlan {
    const beatSummary =
      output.timelineStructure?.map((item) => item.section).filter((item) => item.length > 0) ?? [];
    const fallbackBeat = output.shotList[0]?.scene?.trim() || output.title.trim();

    return {
      title: output.title.trim(),
      storyLogline:
        output.creativeDirection?.coreIdea?.trim() ||
        output.summary?.shootGoal?.trim() ||
        '围绕当前 brief 生成一组完整日常影像。',
      beatSummary: beatSummary.length > 0 ? beatSummary : [fallbackBeat],
      shotTasks: output.shotList.map((item, index) => ({
        id: `shot_${String(item.id).trim()}`,
        orderIndex: index + 1,
        title: (item.scene?.trim() || item.caption?.trim() || `镜头 ${index + 1}`).slice(0, 120),
        goal: (item.content?.trim() || item.caption?.trim() || '完成当前镜头拍摄').slice(0, 600),
        shotType: (item.shotType?.trim() || '中景').slice(0, 120),
        durationSuggestSec: item.durationSec ?? 5,
        compositionHint: (item.scene?.trim() || item.caption?.trim() || '保持主体清晰稳定').slice(0, 240),
        actionHint: (item.subjectAction?.trim() || '按镜头目标完成动作').slice(0, 240),
        status: shotStatusSchema.parse('Planned'),
        capturedClipIds: [],
        latestReview: null,
        beatLabel: (item.scene?.trim() || `段落 ${index + 1}`).slice(0, 120),
        whyThisShotMatters: (item.content?.trim() || '帮助这组影像完成叙事推进').slice(0, 240),
        successChecklist: [
          (item.caption?.trim() || '主体清晰').slice(0, 120),
          '画面稳定',
        ],
        difficultyHint: '先稳住镜头再录',
        retakePriority: 'Medium',
      })),
    };
  }
}

export type { AssemblySuggestion, ClipReview, DirectorPlan };
