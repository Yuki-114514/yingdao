import Fastify, { type FastifyInstance } from 'fastify';
import { readEnv } from './config/env.js';
import { failureEnvelope, successEnvelope } from './lib/envelope.js';
import { registerAiRoutes } from './routes/ai.js';
import {
  type AiDirectorService,
  DefaultAiDirectorService,
} from './services/aiDirectorService.js';
import { OpenAiCompatibleProviderClient } from './services/providerClient.js';

export type RateLimitOptions = {
  maxRequests: number;
  windowMs: number;
};

export type BuildAppOptions = {
  aiDirectorService?: AiDirectorService;
  appToken?: string;
  rateLimit?: RateLimitOptions;
};

type RateLimitBucket = {
  count: number;
  resetAt: number;
};

const REQUEST_BODY_LIMIT_BYTES = 128 * 1024;
const APP_TOKEN_HEADER = 'x-yingdao-app-token';
const HEALTH_CHECK_PATH = '/health';
const DEFAULT_RATE_LIMIT_MAX_REQUESTS = 30;
const DEFAULT_RATE_LIMIT_WINDOW_MS = 60000;

export function buildApp(options: BuildAppOptions = {}): FastifyInstance {
  const app = Fastify({
    bodyLimit: REQUEST_BODY_LIMIT_BYTES,
  });
  const env = options.aiDirectorService ? undefined : readEnv();
  const aiDirectorService = options.aiDirectorService ?? createAiDirectorService(env);
  const appToken = (options.appToken ?? env?.APP_TOKEN ?? '').trim();
  const rateLimit = options.rateLimit ?? {
    maxRequests: env?.RATE_LIMIT_MAX_REQUESTS ?? DEFAULT_RATE_LIMIT_MAX_REQUESTS,
    windowMs: env?.RATE_LIMIT_WINDOW_MS ?? DEFAULT_RATE_LIMIT_WINDOW_MS,
  };

  registerRequestGuards(app, appToken, rateLimit);
  registerHealthRoute(app);
  registerAiRoutes(app, aiDirectorService);

  return app;
}

function registerHealthRoute(app: FastifyInstance): void {
  app.get(HEALTH_CHECK_PATH, async () => successEnvelope({ status: 'ok' }));
}

function registerRequestGuards(
  app: FastifyInstance,
  appToken: string,
  rateLimit: RateLimitOptions,
): void {
  const buckets = new Map<string, RateLimitBucket>();

  app.addHook('onRequest', async (request, reply) => {
    if (request.url === HEALTH_CHECK_PATH) {
      return;
    }

    const now = Date.now();
    const key = request.ip;
    const bucket = buckets.get(key);
    const nextBucket = bucket && bucket.resetAt > now
      ? { count: bucket.count + 1, resetAt: bucket.resetAt }
      : { count: 1, resetAt: now + rateLimit.windowMs };

    buckets.set(key, nextBucket);

    if (nextBucket.count > rateLimit.maxRequests) {
      return reply.code(429).send(failureEnvelope('请求过于频繁，请稍后再试。'));
    }

    const requestAppToken = String(request.headers[APP_TOKEN_HEADER] ?? '').trim();
    if (appToken && requestAppToken !== appToken) {
      return reply.code(401).send(failureEnvelope('请求未授权。'));
    }
  });
}

function createAiDirectorService(env = readEnv()): AiDirectorService {
  const providerClient = new OpenAiCompatibleProviderClient({
    apiKey: env.MODEL_API_KEY,
    modelName: env.MODEL_NAME,
    timeoutMs: env.REQUEST_TIMEOUT_MS,
    baseUrl: env.MODEL_BASE_URL,
    useJsonResponseFormat: env.MODEL_JSON_RESPONSE_FORMAT,
    maxTokens: env.MODEL_MAX_TOKENS,
    reasoningEffort: env.MODEL_REASONING_EFFORT,
  });

  return new DefaultAiDirectorService(providerClient);
}
