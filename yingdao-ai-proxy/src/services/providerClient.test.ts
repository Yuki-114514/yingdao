import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { OpenAiCompatibleProviderClient } from './providerClient.js';

describe('OpenAiCompatibleProviderClient', () => {
  it('parses JSON string content from an OpenAI-compatible response', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: '{"ok":true,"answer":"hi"}',
              },
            },
          ],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    vi.stubGlobal('fetch', fetchMock);

    const client = new OpenAiCompatibleProviderClient({
      apiKey: 'test-key',
      modelName: 'gpt-5.4',
      timeoutMs: 1000,
      baseUrl: 'http://127.0.0.1:8317',
    });

    const result = await client.generateObject({
      systemPrompt: 'return json',
      userPrompt: 'say hi',
      schema: z.object({
        ok: z.boolean(),
        answer: z.string(),
      }),
    });

    expect(result).toEqual({ ok: true, answer: 'hi' });
  });

  it('rejects schema-mismatched JSON string content from an OpenAI-compatible response', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: '{"task":"generate_director_plan","title":"图书馆状态记录"}',
              },
            },
          ],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    vi.stubGlobal('fetch', fetchMock);

    const client = new OpenAiCompatibleProviderClient({
      apiKey: 'test-key',
      modelName: 'gpt-5.4',
      timeoutMs: 1000,
      baseUrl: 'http://127.0.0.1:8317',
    });

    await expect(
      client.generateObject({
        systemPrompt: 'return json',
        userPrompt: 'say hi',
        schema: z.object({
          storyLogline: z.string(),
        }),
      }),
    ).rejects.toMatchObject({
      name: 'ZodError',
    });
  });
});
