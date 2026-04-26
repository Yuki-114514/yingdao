import { z } from 'zod';
import { describe, expect, it } from 'vitest';
import { DefaultAiDirectorService } from './aiDirectorService.js';
import type { ProviderClient } from './providerClient.js';
import type { CreativeBrief, ShotTask } from '../schemas/ai.js';

const sampleBrief: CreativeBrief = {
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

const sampleShotTask: ShotTask = {
  id: 'shot_01',
  orderIndex: 1,
  title: '细节照片',
  goal: '拍清楚食物细节',
  shotType: '照片特写',
  durationSuggestSec: 1,
  compositionHint: '靠近主体',
  actionHint: '拍一张清楚照片',
  status: 'Planned',
  capturedClipIds: [],
  latestReview: null,
  beatLabel: '主体细节',
  whyThisShotMatters: '让组图更有重点',
  successChecklist: ['主体清晰'],
  difficultyHint: '避开杂乱背景',
  retakePriority: 'Medium',
};

describe('DefaultAiDirectorService', () => {
  it('guides photo director plans without short-video framing', async () => {
    let capturedSystemPrompt = '';

    const providerClient: ProviderClient = {
      async generateObject<T>(input: { systemPrompt: string; userPrompt: string; schema: z.ZodSchema<T> }): Promise<T> {
        capturedSystemPrompt = input.systemPrompt;
        return {
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
        } as T;
      },
    };

    const service = new DefaultAiDirectorService(providerClient);

    await service.generateDirectorPlan({ ...sampleBrief, mediaType: 'Photo' });

    expect(capturedSystemPrompt).toContain('日常影像');
    expect(capturedSystemPrompt).toContain('mediaType 为 Photo');
    expect(capturedSystemPrompt).toContain('避免录制、运镜、收音建议');
    expect(capturedSystemPrompt).not.toContain('校园短视频导演助手');
    expect(capturedSystemPrompt).toContain('title');
    expect(capturedSystemPrompt).toContain('storyLogline');
    expect(capturedSystemPrompt).toContain('beatSummary');
    expect(capturedSystemPrompt).toContain('shotTasks');
    expect(capturedSystemPrompt).toContain('durationSuggestSec');
    expect(capturedSystemPrompt).toContain('retakePriority');
    expect(capturedSystemPrompt).toContain('beatSummary 必须是字符串数组');
    expect(capturedSystemPrompt).toContain('latestReview 必须为 null');
  });

  it('maps an alternate upstream director plan shape into the Android contract', async () => {
    const providerClient: ProviderClient = {
      async generateObject<T>(): Promise<T> {
        return {
          title: '图书馆状态记录',
          summary: {
            shootGoal: '拍出一条今天就能分享的校园短片',
          },
          shotList: [
            {
              id: 1,
              durationSec: 5,
              shotType: '全景',
              content: '拍图书馆安静整洁的空间，建立环境氛围',
              subjectAction: '人物未入镜或远处出现',
              scene: '图书馆入口或馆内大环境',
              caption: '今天的图书馆，还是很安静',
            },
          ],
        } as T;
      },
    };

    const service = new DefaultAiDirectorService(providerClient);

    const result = await service.generateDirectorPlan(sampleBrief);

    expect(result).toMatchObject({
      title: '图书馆状态记录',
      storyLogline: '拍出一条今天就能分享的校园短片',
      beatSummary: ['图书馆入口或馆内大环境'],
      shotTasks: [
        {
          id: 'shot_1',
          orderIndex: 1,
          title: '图书馆入口或馆内大环境',
          goal: '拍图书馆安静整洁的空间，建立环境氛围',
          shotType: '全景',
          durationSuggestSec: 5,
          actionHint: '人物未入镜或远处出现',
          beatLabel: '图书馆入口或馆内大环境',
        },
      ],
    });
  });

  it('builds clip review locally without waiting on the upstream model', async () => {
    const providerClient: ProviderClient = {
      async generateObject<T>(): Promise<T> {
        throw new Error('clip review should not call upstream');
      },
    };

    const service = new DefaultAiDirectorService(providerClient);

    const result = await service.reviewClip(sampleShotTask, 1, 'Photo');

    expect(result).toMatchObject({
      clipId: '',
      usable: true,
      issues: expect.arrayContaining(['情绪记忆点还不够强']),
    });
    expect(result.nextAction).toContain('继续推进下一个任务');
  });

  it('defaults omitted clip review media type to video in the local review', async () => {
    const providerClient: ProviderClient = {
      async generateObject<T>(): Promise<T> {
        throw new Error('clip review should not call upstream');
      },
    };

    const service = new DefaultAiDirectorService(providerClient);

    const result = await service.reviewClip(
      {
        ...sampleShotTask,
        shotType: '中景',
        durationSuggestSec: 4,
        retakePriority: 'Low',
      },
      1,
    );

    expect(result.usable).toBe(true);
    expect(result.issues).toEqual(['这一条已经达到了当前镜头目标']);
    expect(result.suggestion).toContain('镜头');
  });

  it('builds assembly suggestion locally without waiting on the upstream model', async () => {
    const providerClient: ProviderClient = {
      async generateObject<T>(): Promise<T> {
        throw new Error('assembly suggestion should not call upstream');
      },
    };

    const service = new DefaultAiDirectorService(providerClient);

    const result = await service.buildAssembly({
      id: 'proj_1',
      title: '测试项目',
      templateId: 'campus_life',
      status: 'ReviewReady',
      brief: { ...sampleBrief, mediaType: 'Photo' },
      directorPlan: {
        title: '图书馆状态记录',
        storyLogline: '记录图书馆的一天。',
        beatSummary: ['建立环境', '补足细节'],
        shotTasks: [
          {
            ...sampleShotTask,
            id: 'shot_01',
            status: 'Approved',
            capturedClipIds: ['clip_1'],
            latestReview: null,
          },
          {
            ...sampleShotTask,
            id: 'shot_02',
            orderIndex: 2,
            beatLabel: '细节补强',
            status: 'Planned',
          },
        ],
      },
      clips: [
        {
          id: 'clip_1',
          shotTaskId: 'shot_01',
          localPath: 'content://clip/1',
          durationSec: 0,
          thumbnailLabel: '开场',
          mediaType: 'Photo',
          review: null,
        },
      ],
      assemblySuggestion: null,
    });

    expect(result.orderedClipIds).toEqual(['clip_1']);
    expect(result.missingShotIds).toEqual(['shot_02']);
    expect(result.missingBeatLabels).toEqual(['细节补强']);
    expect(result.captionDraft[0]).toContain('图书馆');
    expect(result.selectionReasonByClipId).toEqual({
      clip_1: '保留它是因为它承担了“主体细节”。',
    });
  });
});
