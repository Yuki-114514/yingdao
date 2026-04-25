import { z } from 'zod';
import { describe, expect, it } from 'vitest';
import { DefaultAiDirectorService } from './aiDirectorService.js';
import type { ProviderClient } from './providerClient.js';
import type { CreativeBrief } from '../schemas/ai.js';

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

describe('DefaultAiDirectorService', () => {
  it('includes Android contract field names in the director plan system prompt', async () => {
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

    await service.generateDirectorPlan(sampleBrief);

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

  it('normalizes string captionDraft into an array for assembly suggestion output', async () => {
    const providerClient: ProviderClient = {
      async generateObject<T>(): Promise<T> {
        return {
          orderedClipIds: ['clip_1'],
          missingShotIds: [],
          titleOptions: ['图书馆状态记录'],
          captionDraft: '今天的图书馆，安静又刚刚好。',
          missingBeatLabels: ['平静收尾'],
          editingDirection: '先用 clip_1 建立氛围。',
          selectionReasonByClipId: {
            clip_1: '画面稳定，适合作为开场。',
          },
        } as T;
      },
    };

    const service = new DefaultAiDirectorService(providerClient);

    const result = await service.buildAssembly({
      id: 'proj_1',
      title: '测试项目',
      templateId: 'campus_life',
      status: 'ReviewReady',
      brief: sampleBrief,
      directorPlan: null,
      clips: [],
      assemblySuggestion: null,
    });

    expect(result.captionDraft).toEqual(['今天的图书馆，安静又刚刚好。']);
  });
});
