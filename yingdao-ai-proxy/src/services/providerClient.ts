import { z } from 'zod';

export type ProviderClientConfig = {
  apiKey: string;
  modelName: string;
  timeoutMs: number;
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
  }): Promise<T> {
    const startedAt = Date.now();
    const url = this.resolveUrl();
    const requestBody = this.buildRequestBody(input);

    try {
      console.info('ai_provider_request_start', {
        model: this.config.modelName,
        host: new URL(url).host,
        maxTokens: requestBody.max_tokens ?? null,
        jsonResponseFormat: this.config.useJsonResponseFormat !== false,
      });

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.config.apiKey}`,
        },
        body: JSON.stringify(requestBody),
        signal: AbortSignal.timeout(input.timeoutMs ?? this.config.timeoutMs),
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
        model: this.config.modelName,
        durationMs: Date.now() - startedAt,
        contentChars: text.length,
      });

      return input.schema.parse(JSON.parse(text) as unknown);
    } catch (error) {
      console.warn('ai_provider_request_failed', {
        model: this.config.modelName,
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
  }): Record<string, unknown> {
    const baseBody: Record<string, unknown> = {
      model: this.config.modelName,
      temperature: 0.2,
      messages: [
        {
          role: 'system',
          content: input.systemPrompt,
        },
        {
          role: 'user',
          content: input.userPrompt,
        },
      ],
    };

    const maxTokens = input.maxTokens ?? this.config.maxTokens;
    if (maxTokens !== undefined) {
      baseBody.max_tokens = maxTokens;
    }

    if (this.config.reasoningEffort !== undefined) {
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
