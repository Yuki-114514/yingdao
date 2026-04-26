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
    const response = await fetch(this.resolveUrl(), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.config.apiKey}`,
      },
      body: JSON.stringify(this.buildRequestBody(input)),
      signal: AbortSignal.timeout(input.timeoutMs ?? this.config.timeoutMs),
    });

    if (!response.ok) {
      throw new Error(`Upstream returned HTTP ${response.status}`);
    }

    const payload = (await response.json()) as OpenAiCompatibleResponse;
    const text = payload.choices?.[0]?.message?.content?.trim();
    if (!text) {
      throw new Error('AI 返回了空内容。');
    }

    return input.schema.parse(JSON.parse(text) as unknown);
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
