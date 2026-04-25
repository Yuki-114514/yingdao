import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const PLACEHOLDER_APP_TOKEN = 'change_me_for_cloud_deployments';
const booleanEnvSchema = z.preprocess((value) => {
  if (typeof value !== 'string') {
    return value;
  }

  if (value.toLowerCase() === 'true') {
    return true;
  }

  if (value.toLowerCase() === 'false') {
    return false;
  }

  return value;
}, z.boolean());

const envSchema = z
  .object({
    NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
    HOST: z.string().default('127.0.0.1'),
    PORT: z.coerce.number().int().default(8787),
    MODEL_PROVIDER: z.enum(['openai-compatible']).default('openai-compatible'),
    MODEL_BASE_URL: z.string().url().default('http://127.0.0.1:8317'),
    MODEL_API_KEY: z.string().min(1),
    MODEL_NAME: z.string().min(1).default('gpt-4o-mini'),
    MODEL_JSON_RESPONSE_FORMAT: booleanEnvSchema.default(true),
    REQUEST_TIMEOUT_MS: z.coerce.number().int().positive().default(120000),
    APP_TOKEN: z.string().default(''),
    RATE_LIMIT_MAX_REQUESTS: z.coerce.number().int().positive().default(30),
    RATE_LIMIT_WINDOW_MS: z.coerce.number().int().positive().default(60000),
  })
  .superRefine((env, context) => {
    const requiresAppToken = env.NODE_ENV === 'production' || env.HOST !== '127.0.0.1';
    if (requiresAppToken && (!env.APP_TOKEN || env.APP_TOKEN === PLACEHOLDER_APP_TOKEN)) {
      context.addIssue({
        code: 'custom',
        path: ['APP_TOKEN'],
        message: 'APP_TOKEN must be configured for cloud deployments.',
      });
    }
  });

export type AppEnv = z.infer<typeof envSchema>;

export function readEnv(source: NodeJS.ProcessEnv = process.env): AppEnv {
  return envSchema.parse(source);
}
