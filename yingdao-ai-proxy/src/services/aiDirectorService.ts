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

const DIRECTOR_PLAN_AI_TIMEOUT_MS = 20000;

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
    try {
      return await this.generateStructured({
        schema: directorPlanSchema,
        systemPrompt:
          '你是日常影像拍摄助手，覆盖日常、美食、旅行、宠物、穿搭、学习、朋友聚会等主题，不局限校园。只返回 JSON 对象。字段只能有：title、storyLogline、beatSummary、shotTasks。beatSummary 必须是字符串数组。shotTasks 每项只能有：id、orderIndex、title、goal、shotType、durationSuggestSec、compositionHint、actionHint、status、capturedClipIds、latestReview、beatLabel、whyThisShotMatters、successChecklist、difficultyHint、retakePriority。latestReview 必须为 null。status 只能是 Planned、Active、Captured、Approved、RetakeSuggested、Skipped。retakePriority 只能是 Low、Medium、High。镜头数不超过 5。mediaType 为 Photo 时输出拍照任务，避免录制、运镜、收音建议；mediaType 为 Video 时输出视频镜头任务。每个字段尽量简洁。',
        userPrompt: JSON.stringify({ task: 'generate_director_plan', brief }),
        timeoutMs: DIRECTOR_PLAN_AI_TIMEOUT_MS,
      });
    } catch {
      return this.buildLocalDirectorPlan(brief);
    }
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

  private buildLocalDirectorPlan(brief: CreativeBrief): DirectorPlan {
    const shotTasks = brief.mediaType === 'Photo'
      ? buildLocalPhotoShotTasks(brief)
      : buildLocalVideoShotTasks(brief);

    return directorPlanSchema.parse({
      title: brief.title,
      storyLogline: buildLocalStoryLogline(brief),
      beatSummary: buildLocalBeatSummary(brief),
      shotTasks,
    });
  }

  private async generateStructured<T>(input: {
    schema: z.ZodSchema<T>;
    systemPrompt: string;
    userPrompt: string;
    timeoutMs?: number;
  }): Promise<T> {
    try {
      const output = await this.providerClient.generateObject({
        systemPrompt: input.systemPrompt,
        userPrompt: input.userPrompt,
        schema: z.unknown() as z.ZodSchema<T>,
        timeoutMs: input.timeoutMs,
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

function buildLocalStoryLogline(brief: CreativeBrief): string {
  const subjectPrefix = brief.soloMode || brief.castCount <= 1 ? '一个人' : '一群人';
  const output = brief.mediaType === 'Photo' ? '照片组' : '影像记录';
  return `${subjectPrefix}围绕“${brief.highlightSubject}”展开，用${brief.style}的方式记录${brief.theme}，形成一组${brief.mood}的${output}。`;
}

function buildLocalBeatSummary(brief: CreativeBrief): string[] {
  if (brief.mediaType === 'Photo') {
    return ['先拍清环境和主题', '补足主体、细节和情绪', '用一张氛围照收住今天'];
  }

  return ['建立场景和人物状态', '用动作和细节推进内容', '用情绪镜头完成收束'];
}

function buildLocalPhotoShotTasks(brief: CreativeBrief): ShotTask[] {
  return [
    makeShotTask({
      id: 'shot_01',
      orderIndex: 1,
      title: '环境建立照',
      goal: `先拍一张能说明“${brief.theme}”发生在哪里的照片`,
      shotType: '照片 / 环境',
      durationSuggestSec: 1,
      compositionHint: '保留环境层次，把主体放在画面三分之一附近',
      actionHint: '先站稳再拍一张，画面里要看得出地点和氛围',
      beatLabel: '环境建立',
      whyThisShotMatters: '让这组照片一开始就有场景，而不是零散素材',
      successChecklist: ['环境信息清楚', '主体位置明确'],
      difficultyHint: '第一张不要急着贴近，先给环境一点空间。',
      retakePriority: 'High',
    }),
    makeShotTask({
      id: 'shot_02',
      orderIndex: 2,
      title: '主体明确照',
      goal: `拍清“${brief.highlightSubject}”，让这组照片有明确主角`,
      shotType: '照片 / 主体',
      durationSuggestSec: 1,
      compositionHint: '背景尽量干净，主体不要贴边',
      actionHint: '等主体自然进入最好看的状态，再拍一张',
      beatLabel: '主体明确',
      whyThisShotMatters: '日常照片最先要让人知道你想留下什么',
      successChecklist: ['主体清晰', '背景不抢戏'],
      difficultyHint: '主体照不需要复杂，先拍清楚比拍花哨更重要。',
      retakePriority: 'High',
    }),
    makeShotTask({
      id: 'shot_03',
      orderIndex: 3,
      title: '细节特写照',
      goal: '用一张细节照片补足今天最有记忆点的部分',
      shotType: '照片 / 特写',
      durationSuggestSec: 1,
      compositionHint: '靠近细节，画面只保留一到两个重点',
      actionHint: '拍一张最能代表今天的小物、动作或表情细节',
      beatLabel: '细节补强',
      whyThisShotMatters: '细节会让这组照片更像真实生活，而不是打卡照',
      successChecklist: ['细节主体明确', '光线干净'],
      difficultyHint: '特写不要贪多，靠近一个最有代表性的细节。',
      retakePriority: 'Medium',
    }),
    makeShotTask({
      id: 'shot_04',
      orderIndex: 4,
      title: '收尾氛围照',
      goal: '最后拍一张能给这组照片收住情绪的画面',
      shotType: '照片 / 氛围',
      durationSuggestSec: 1,
      compositionHint: '画面留一点空白，适合之后放标题或文案',
      actionHint: '退后一步拍环境、桌面、背影或光线，让今天有一个结束感',
      beatLabel: '结尾收束',
      whyThisShotMatters: '让这组照片有完整的开始和结束',
      successChecklist: ['氛围完整', '画面留白舒服'],
      difficultyHint: '收尾照可以安静一点，不需要再塞很多信息。',
      retakePriority: 'Low',
    }),
  ];
}

function buildLocalVideoShotTasks(brief: CreativeBrief): ShotTask[] {
  const baseDuration = brief.timePressure === 'High' ? 3 : 4;
  return [
    makeShotTask({
      id: 'shot_01',
      orderIndex: 1,
      title: '状态开场',
      goal: `先建立“${brief.theme}”的场景和人物状态`,
      shotType: '中景',
      durationSuggestSec: baseDuration,
      compositionHint: '人物位于画面三分之一，给前进方向留白',
      actionHint: '边走边进入画面，不要急着看镜头',
      beatLabel: '开场建立',
      whyThisShotMatters: '让观众快速知道这条片子在记录谁、记录什么',
      successChecklist: ['主体清晰', '画面稳定'],
      difficultyHint: '第一条镜头最容易着急，宁可慢一点。',
      retakePriority: 'High',
    }),
    makeShotTask({
      id: 'shot_02',
      orderIndex: 2,
      title: '动作细节',
      goal: `用细节镜头把“${brief.highlightSubject}”补出来`,
      shotType: '近景',
      durationSuggestSec: baseDuration,
      compositionHint: '靠近最能代表今天状态的动作细节',
      actionHint: '抓最能代表今天状态的手部或身体动作',
      beatLabel: '状态推进',
      whyThisShotMatters: '让观众不只是看到人，还能看到当下在发生什么',
      successChecklist: ['动作被拍完整', '细节主体明确'],
      difficultyHint: '细节镜头不要贪多，优先拍最能代表今天的一处。',
      retakePriority: 'High',
    }),
    makeShotTask({
      id: 'shot_03',
      orderIndex: 3,
      title: '情绪记忆点',
      goal: '补一条让人记住今天情绪的镜头',
      shotType: '特写',
      durationSuggestSec: baseDuration,
      compositionHint: '背景尽量简洁，把情绪集中在人物表情或动作停顿上',
      actionHint: '抓住停顿、抬头、回头或轻微微笑的一瞬间',
      beatLabel: '情绪记忆点',
      whyThisShotMatters: '让这条片子有记忆点，而不只是流程记录',
      successChecklist: ['情绪主体明确', '画面不要晃'],
      difficultyHint: '情绪镜头最怕刻意，先让动作自然发生。',
      retakePriority: 'Medium',
    }),
    makeShotTask({
      id: 'shot_04',
      orderIndex: 4,
      title: '收束镜头',
      goal: '给成片一个能落下来的结尾',
      shotType: '远景',
      durationSuggestSec: baseDuration,
      compositionHint: '画面尽量干净，保留可放标题或片尾文案的位置',
      actionHint: '镜头最后两秒尽量稳定，给收尾留呼吸空间',
      beatLabel: '结尾收束',
      whyThisShotMatters: '让今天的记录有真正的结束，而不是突然停住',
      successChecklist: ['最后两秒稳定', '画面留有结尾空间'],
      difficultyHint: '收尾镜头不要急着停，给自己多留一秒。',
      retakePriority: 'High',
    }),
  ];
}

function makeShotTask(input: Omit<ShotTask, 'status' | 'capturedClipIds' | 'latestReview'>): ShotTask {
  return {
    ...input,
    title: input.title.slice(0, 240),
    goal: input.goal.slice(0, 600),
    compositionHint: input.compositionHint.slice(0, 240),
    actionHint: input.actionHint.slice(0, 240),
    whyThisShotMatters: input.whyThisShotMatters.slice(0, 240),
    difficultyHint: input.difficultyHint.slice(0, 240),
    status: 'Planned',
    capturedClipIds: [],
    latestReview: null,
  };
}

export type { AssemblySuggestion, ClipReview, DirectorPlan };
