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
        '你是日常影像拍摄助手，覆盖日常、美食、旅行、宠物、穿搭、学习、朋友聚会等主题，不局限校园。只返回 JSON 对象。字段只能有：title、storyLogline、beatSummary、shotTasks。beatSummary 必须是字符串数组。shotTasks 每项只能有：id、orderIndex、title、goal、shotType、durationSuggestSec、compositionHint、actionHint、status、capturedClipIds、latestReview、beatLabel、whyThisShotMatters、successChecklist、difficultyHint、retakePriority。latestReview 必须为 null。status 只能是 Planned、Active、Captured、Approved、RetakeSuggested、Skipped。retakePriority 只能是 Low、Medium、High。镜头数不超过 6。mediaType 为 Photo 时输出拍照任务，避免录制、运镜、收音建议；mediaType 为 Video 时输出视频镜头任务。每个字段尽量简洁。',
      userPrompt: JSON.stringify({ task: 'generate_director_plan', brief }),
    });
  }

  async reviewClip(
    shotTask: ShotTask,
    attemptNumber: number,
    mediaType?: ReviewClipRequest['mediaType'],
  ): Promise<ClipReview> {
    return this.buildLocalClipReview(shotTask, attemptNumber, mediaType ?? 'Video');
  }

  async buildAssembly(project: Project): Promise<AssemblySuggestion> {
    return this.buildLocalAssemblySuggestion(project);
  }

  private buildLocalClipReview(
    shotTask: ShotTask,
    attemptNumber: number,
    mediaType: ReviewClipRequest['mediaType'],
  ): ClipReview {
    const attemptPenalty = Math.max(attemptNumber - 1, 0) * 4;
    const baseScoreByPriority = {
      Low: 84,
      Medium: 78,
      High: 72,
    } satisfies Record<ShotTask['retakePriority'], number>;
    const baseScore = baseScoreByPriority[shotTask.retakePriority];
    const stabilityScore = clampScore(baseScore - attemptPenalty, 60, 94);
    const subjectScore = clampScore(baseScore + 4 - attemptPenalty, 62, 96);
    const compositionScore = clampScore(baseScore + 2 - attemptPenalty, 61, 95);
    const emotionScore = clampScore(
      baseScore + (shotTask.beatLabel === '情绪记忆点' ? 6 : 1) - attemptPenalty,
      60,
      96,
    );
    const score = Math.round((stabilityScore + subjectScore + compositionScore + emotionScore) / 4);
    const usable = score >= 80;
    const isPhotoTask =
      mediaType === 'Photo' || shotTask.shotType.includes('照片') || shotTask.durationSuggestSec <= 1;
    const issues = buildReviewIssues({
      stabilityScore,
      subjectScore,
      compositionScore,
      emotionScore,
      isPhotoTask,
    });
    const unit = isPhotoTask ? '照片' : '镜头';

    return {
      clipId: '',
      usable,
      score,
      issues,
      suggestion: usable
        ? `已经可用，重点看是否还想补一个更有层次的${unit}版本。`
        : `建议补拍，先把当前${unit}的关键交付补齐。`,
      stabilityScore,
      subjectScore,
      compositionScore,
      emotionScore,
      keepReason: usable ? `这${isPhotoTask ? '张' : '条'}素材已经完成“${shotTask.whyThisShotMatters}”。` : '',
      retakeReason: usable ? '' : `当前最影响保留的是：${issues[0]}。`,
      nextAction: usable
        ? `这${isPhotoTask ? '张' : '条'}可以先通过，继续推进下一个任务。`
        : shotTask.successChecklist[0]
          ? `优先补到：${shotTask.successChecklist[0]}。`
          : `建议再拍一个更清楚的${unit}版本。`,
    };
  }

  private buildLocalAssemblySuggestion(project: Project): AssemblySuggestion {
    const shotTasks = project.directorPlan?.shotTasks ?? [];
    const approvedShots = shotTasks.filter((shot) => shot.status === 'Approved');
    const missingShots = shotTasks.filter((shot) => shot.status !== 'Approved' && shot.status !== 'Skipped');
    const orderedClipIds = approvedShots.flatMap((shot) => shot.capturedClipIds.slice(-1));
    const missingBeatLabels = unique(missingShots.map((shot) => shot.beatLabel));
    const selectionReasonByClipId = Object.fromEntries(
      approvedShots.flatMap((shot) => {
        const clipId = shot.capturedClipIds.at(-1);
        return clipId ? [[clipId, `保留它是因为它承担了“${shot.beatLabel}”。`]] : [];
      }),
    );
    const isPhotoProject = project.brief.mediaType === 'Photo';

    return {
      orderedClipIds,
      missingShotIds: missingShots.map((shot) => shot.id),
      titleOptions: buildTitleOptions(project.brief, isPhotoProject),
      captionDraft: buildCaptionDraft(project.brief, isPhotoProject),
      missingBeatLabels,
      editingDirection:
        missingBeatLabels.length === 0
          ? buildCompletedEditingDirection(isPhotoProject)
          : `建议先补齐 ${missingBeatLabels.join('、')}，再做最终整理。`,
      selectionReasonByClipId,
    };
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

function clampScore(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function buildReviewIssues(input: {
  stabilityScore: number;
  subjectScore: number;
  compositionScore: number;
  emotionScore: number;
  isPhotoTask: boolean;
}): string[] {
  const issues = [
    input.stabilityScore < 78 ? (input.isPhotoTask ? '画面清晰度还可以再稳一点' : '画面还不够稳') : '',
    input.subjectScore < 80 ? '主体还可以再明确' : '',
    input.compositionScore < 80 ? '构图重心可以再收紧' : '',
    input.emotionScore < 80 ? '情绪记忆点还不够强' : '',
  ].filter((item) => item.length > 0);

  if (issues.length > 0) {
    return issues;
  }

  return [input.isPhotoTask ? '这张已经达到了当前拍照目标' : '这一条已经达到了当前镜头目标'];
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

function buildTitleOptions(brief: CreativeBrief, isPhotoProject: boolean): string[] {
  const output = isPhotoProject ? '照片' : '小片子';
  return [
    `${brief.theme}的${brief.mood}时刻`,
    `今天的${brief.highlightSubject}`,
    `把${brief.theme}拍成一${isPhotoProject ? '组' : '条'}${output}`,
  ];
}

function buildCaptionDraft(brief: CreativeBrief, isPhotoProject: boolean): string[] {
  const output = isPhotoProject ? '这组照片' : '这条片子';
  return [
    `今天想拍下的，不只是${brief.theme}，还有“${brief.highlightSubject}”这一刻的状态。`,
    `${output}想留下的是一种${brief.mood}的生活节奏。`,
  ];
}

function buildCompletedEditingDirection(isPhotoProject: boolean): string {
  return isPhotoProject
    ? '当前照片已经覆盖了环境、主体、细节、情绪和收尾，可以按这个顺序直接挑图发布。'
    : '当前镜头已经覆盖了开场、人物和收尾，可以按环境、人物、情绪、收束的顺序直接成片。';
}

export type { AssemblySuggestion, ClipReview, DirectorPlan };
