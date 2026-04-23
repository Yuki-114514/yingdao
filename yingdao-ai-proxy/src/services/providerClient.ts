import { z } from 'zod';

export type ProviderClientConfig = {
  apiKey: string;
  modelName: string;
  timeoutMs: number;
  baseUrl?: string;
};

export interface ProviderClient {
  generateObject<T>(input: {
    systemPrompt: string;
    userPrompt: string;
    schema: z.ZodSchema<T>;
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
  }): Promise<T> {
    const response = await fetch(this.resolveUrl(), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.config.apiKey}`,
      },
      body: JSON.stringify({
        model: this.config.modelName,
        temperature: 0.2,
        response_format: { type: 'json_object' },
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
      }),
      signal: AbortSignal.timeout(this.config.timeoutMs),
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

  private resolveUrl(): string {
    const baseUrl = this.config.baseUrl?.trim() || 'http://127.0.0.1:8317';
    return `${baseUrl.replace(/\/$/, '')}/v1/chat/completions`;
  }
}
