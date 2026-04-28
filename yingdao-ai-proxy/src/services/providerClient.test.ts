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

  it('omits response_format when JSON mode is disabled for providers that do not support it', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: '{"ok":true}',
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
      modelName: 'deepseek-ai/deepseek-v4-flash',
      timeoutMs: 1000,
      baseUrl: 'https://integrate.api.nvidia.com',
      useJsonResponseFormat: false,
    });

    await client.generateObject({
      systemPrompt: 'return json',
      userPrompt: 'ok',
      schema: z.object({
        ok: z.boolean(),
      }),
    });

    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const requestBody = JSON.parse(firstCall[1].body as string) as Record<string, unknown>;
    expect(requestBody.response_format).toBeUndefined();
  });

  it('passes latency tuning parameters to OpenAI-compatible providers', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: '{"ok":true}',
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
      modelName: 'deepseek-ai/deepseek-v4-flash',
      timeoutMs: 1000,
      baseUrl: 'https://integrate.api.nvidia.com',
      useJsonResponseFormat: false,
      maxTokens: 1200,
      reasoningEffort: 'none',
    });

    await client.generateObject({
      systemPrompt: 'return json',
      userPrompt: 'ok',
      schema: z.object({
        ok: z.boolean(),
      }),
    });

    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const requestBody = JSON.parse(firstCall[1].body as string) as Record<string, unknown>;
    expect(requestBody.max_tokens).toBe(1200);
    expect(requestBody.reasoning_effort).toBe('none');
  });

  it('allows a request to override the configured max token budget', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: '{"ok":true}',
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
      modelName: 'deepseek-ai/deepseek-v4-flash',
      timeoutMs: 1000,
      baseUrl: 'https://integrate.api.nvidia.com',
      useJsonResponseFormat: false,
      maxTokens: 1200,
      reasoningEffort: 'none',
    });

    await client.generateObject({
      systemPrompt: 'return json',
      userPrompt: 'ok',
      schema: z.object({
        ok: z.boolean(),
      }),
      maxTokens: 450,
    });

    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const requestBody = JSON.parse(firstCall[1].body as string) as Record<string, unknown>;
    expect(requestBody.max_tokens).toBe(450);
  });

  it('retries with fallback models when the primary upstream call fails', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('primary timed out'))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            choices: [
              {
                message: {
                  content: '{"ok":true}',
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
      modelName: 'deepseek-ai/deepseek-v4-pro',
      fallbackModelNames: ['meta/llama-3.1-8b-instruct'],
      timeoutMs: 240000,
      attemptTimeoutMs: 45000,
      baseUrl: 'https://integrate.api.nvidia.com',
      useJsonResponseFormat: false,
    });

    const result = await client.generateObject({
      systemPrompt: 'return json',
      userPrompt: 'ok',
      schema: z.object({
        ok: z.boolean(),
      }),
    });

    const primaryCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const fallbackCall = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    const primaryBody = JSON.parse(primaryCall[1].body as string) as Record<string, unknown>;
    const fallbackBody = JSON.parse(fallbackCall[1].body as string) as Record<string, unknown>;

    expect(result).toEqual({ ok: true });
    expect(primaryBody.model).toBe('deepseek-ai/deepseek-v4-pro');
    expect(fallbackBody.model).toBe('meta/llama-3.1-8b-instruct');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('accepts base URLs with or without the OpenAI v1 path', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: '{"ok":true}',
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
      modelName: 'deepseek-ai/deepseek-v4-flash',
      timeoutMs: 1000,
      baseUrl: 'https://integrate.api.nvidia.com/v1',
    });

    await client.generateObject({
      systemPrompt: 'return json',
      userPrompt: 'ok',
      schema: z.object({
        ok: z.boolean(),
      }),
    });

    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(firstCall[0]).toBe('https://integrate.api.nvidia.com/v1/chat/completions');
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
