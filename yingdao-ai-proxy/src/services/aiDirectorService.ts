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

export interface AiDirectorService {
  generateDirectorPlan(brief: CreativeBrief): Promise<DirectorPlan>;
  reviewClip(shotTask: ShotTask, attemptNumber: number): Promise<ClipReview>;
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
        '你是校园短视频导演助手。只返回 JSON 对象。字段只能有：title、storyLogline、beatSummary、shotTasks。beatSummary 必须是字符串数组。shotTasks 每项只能有：id、orderIndex、title、goal、shotType、durationSuggestSec、compositionHint、actionHint、status、capturedClipIds、latestReview、beatLabel、whyThisShotMatters、successChecklist、difficultyHint、retakePriority。latestReview 必须为 null。status 只能是 Planned、Active、Captured、Approved、RetakeSuggested、Skipped。retakePriority 只能是 Low、Medium、High。镜头数不超过 4。每个字段尽量简洁。',
      userPrompt: JSON.stringify({ task: 'generate_director_plan', brief }),
    });
  }

  async reviewClip(shotTask: ShotTask, attemptNumber: number): Promise<ClipReview> {
    return this.generateStructured({
      schema: clipReviewSchema,
      systemPrompt:
        '你是校园短视频拍后点评助手。只返回严格 JSON 对象，不要返回 markdown，不要返回额外解释，不要包含未要求字段。返回字段必须且只能包含：clipId、usable、score、issues、suggestion、stabilityScore、subjectScore、compositionScore、emotionScore、keepReason、retakeReason、nextAction。所有分数字段都必须是 0 到 100 的整数。',
      userPrompt: JSON.stringify({ task: 'review_clip', shotTask, attemptNumber }),
    });
  }

  async buildAssembly(project: Project): Promise<AssemblySuggestion> {
    return this.generateStructured({
      schema: assemblySuggestionSchema,
      systemPrompt:
        '你是校园短视频剪辑助手。只返回严格 JSON 对象，不要返回 markdown，不要返回额外解释，不要包含未要求字段。返回字段必须且只能包含：orderedClipIds、missingShotIds、titleOptions、captionDraft、missingBeatLabels、editingDirection、selectionReasonByClipId。selectionReasonByClipId 必须是对象，键为 clipId，值为对应素材入选原因。',
      userPrompt: JSON.stringify({ task: 'build_assembly', project }),
    });
  }

  private async generateStructured<T>(input: {
    schema: z.ZodSchema<T>;
    systemPrompt: string;
    userPrompt: string;
  }): Promise<T> {
    try {
      const output = await this.providerClient.generateObject({
        systemPrompt: input.systemPrompt,
        userPrompt: input.userPrompt,
        schema: z.unknown() as z.ZodSchema<T>,
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
        latestReview?: unknown;
      };

      if (typeof candidate.latestReview !== 'string') {
        return candidate;
      }

      return {
        ...candidate,
        latestReview: null,
      };
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

  private mapAlternateDirectorPlan(output: z.infer<typeof alternateDirectorPlanSchema>): DirectorPlan {
    const beatSummary =
      output.timelineStructure?.map((item) => item.section).filter((item) => item.length > 0) ?? [];
    const fallbackBeat = output.shotList[0]?.scene?.trim() || output.title.trim();

    return {
      title: output.title.trim(),
      storyLogline:
        output.creativeDirection?.coreIdea?.trim() ||
        output.summary?.shootGoal?.trim() ||
        '围绕当前 brief 生成一条完整校园短片。',
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
        whyThisShotMatters: (item.content?.trim() || '帮助短片完成叙事推进').slice(0, 240),
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
