import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const envSchema = z.object({
  HOST: z.string().default('127.0.0.1'),
  PORT: z.coerce.number().int().default(8787),
  MODEL_PROVIDER: z.enum(['openai-compatible']).default('openai-compatible'),
  MODEL_BASE_URL: z.string().url().default('http://127.0.0.1:8317'),
  MODEL_API_KEY: z.string().min(1),
  MODEL_NAME: z.string().min(1).default('gpt-4o-mini'),
  REQUEST_TIMEOUT_MS: z.coerce.number().int().positive().default(120000),
});

export type AppEnv = z.infer<typeof envSchema>;

export function readEnv(source: NodeJS.ProcessEnv = process.env): AppEnv {
  return envSchema.parse(source);
}
