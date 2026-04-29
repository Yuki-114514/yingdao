import { z } from 'zod';

export type ProviderClientConfig = {
  apiKey: string;
  modelName: string;
  visionModelName?: string;
  fallbackModelNames?: string[];
  timeoutMs: number;
  attemptTimeoutMs?: number;
  baseUrl?: string;
  useJsonResponseFormat?: boolean;
  maxTokens?: number;
  reasoningEffort?: 'none' | 'high' | 'max';
};

export interface ProviderClient {
  generateObject<T>(input: {
    systemPrompt: string;
    userPrompt: string;
    schema: z.ZodSchema<T>;
    timeoutMs?: number;
    maxTokens?: number;
    imageDataUrl?: string;
  }): Promise<T>;
}

type OpenAiCompatibleResponse = {
  choices?: Array<{
    message?: {
      content?: string | null;
    };
  }>;
};

export class OpenAiCompatibleProviderClient implements ProviderClient {
  constructor(private readonly config: ProviderClientConfig) {}

  async generateObject<T>(input: {
    systemPrompt: string;
    userPrompt: string;
    schema: z.ZodSchema<T>;
    timeoutMs?: number;
    maxTokens?: number;
    imageDataUrl?: string;
  }): Promise<T> {
    const modelNames = input.imageDataUrl
      ? [this.config.visionModelName ?? this.config.modelName]
      : [this.config.modelName, ...(this.config.fallbackModelNames ?? [])];
    let lastError: unknown;

    for (const modelName of modelNames) {
      try {
        return await this.generateObjectWithModel(input, modelName);
      } catch (error) {
        lastError = error;
      }
    }

    throw lastError instanceof Error ? lastError : new Error('AI 服务调用失败。');
  }

  private async generateObjectWithModel<T>(
    input: {
      systemPrompt: string;
      userPrompt: string;
      schema: z.ZodSchema<T>;
      timeoutMs?: number;
      maxTokens?: number;
      imageDataUrl?: string;
    },
    modelName: string,
  ): Promise<T> {
    const startedAt = Date.now();
    const url = this.resolveUrl();
    const requestBody = this.buildRequestBody(input, modelName);
    const timeoutMs = input.timeoutMs ?? this.config.attemptTimeoutMs ?? this.config.timeoutMs;

    try {
      console.info('ai_provider_request_start', {
        model: modelName,
        host: new URL(url).host,
        maxTokens: requestBody.max_tokens ?? null,
        hasImage: Boolean(input.imageDataUrl),
        jsonResponseFormat: this.config.useJsonResponseFormat !== false,
      });

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.config.apiKey}`,
        },
        body: JSON.stringify(requestBody),
        signal: AbortSignal.timeout(timeoutMs),
      });

      if (!response.ok) {
        const bodyPreview = (await response.text()).slice(0, 500);
        throw new Error(`Upstream returned HTTP ${response.status}: ${bodyPreview}`);
      }

      const payload = (await response.json()) as OpenAiCompatibleResponse;
      const text = payload.choices?.[0]?.message?.content?.trim();
      if (!text) {
        throw new Error('AI 返回了空内容。');
      }

      console.info('ai_provider_request_success', {
        model: modelName,
        durationMs: Date.now() - startedAt,
        contentChars: text.length,
      });

      return input.schema.parse(JSON.parse(stripJsonCodeFence(text)) as unknown);
    } catch (error) {
      console.warn('ai_provider_request_failed', {
        model: modelName,
        durationMs: Date.now() - startedAt,
        errorName: error instanceof Error ? error.name : 'UnknownError',
        errorMessage: error instanceof Error ? error.message.slice(0, 500) : 'Unknown upstream error',
      });
      throw error;
    }
  }

  private buildRequestBody(input: {
    systemPrompt: string;
    userPrompt: string;
    maxTokens?: number;
    imageDataUrl?: string;
  }, modelName: string): Record<string, unknown> {
    const baseBody: Record<string, unknown> = {
      model: modelName,
      temperature: 0.2,
      messages: [
        {
          role: 'system',
          content: input.systemPrompt,
        },
        {
          role: 'user',
          content: input.imageDataUrl
            ? [
                { type: 'text', text: input.userPrompt },
                { type: 'image_url', image_url: { url: input.imageDataUrl } },
              ]
            : input.userPrompt,
        },
      ],
    };

    const maxTokens = input.maxTokens ?? this.config.maxTokens;
    if (maxTokens !== undefined) {
      baseBody.max_tokens = maxTokens;
    }

    if (
      this.config.reasoningEffort !== undefined &&
      modelName === this.config.modelName &&
      input.imageDataUrl === undefined
    ) {
      baseBody.reasoning_effort = this.config.reasoningEffort;
    }

    if (this.config.useJsonResponseFormat === false) {
      return baseBody;
    }

    return {
      ...baseBody,
      response_format: { type: 'json_object' },
    };
  }

  private resolveUrl(): string {
    const baseUrl = this.config.baseUrl?.trim() || 'http://127.0.0.1:8317';
    const normalizedBaseUrl = baseUrl.replace(/\/$/, '');
    if (normalizedBaseUrl.endsWith('/v1/chat/completions')) {
      return normalizedBaseUrl;
    }
    if (normalizedBaseUrl.endsWith('/v1')) {
      return `${normalizedBaseUrl}/chat/completions`;
    }
    return `${normalizedBaseUrl}/v1/chat/completions`;
  }
}

function stripJsonCodeFence(text: string): string {
  const trimmed = text.trim();
  const fencedJson = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return fencedJson?.[1]?.trim() ?? trimmed;
}
