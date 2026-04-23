import Fastify, { type FastifyInstance } from 'fastify';
import { readEnv } from './config/env.js';
import { registerAiRoutes } from './routes/ai.js';
import {
  type AiDirectorService,
  DefaultAiDirectorService,
} from './services/aiDirectorService.js';
import { OpenAiCompatibleProviderClient } from './services/providerClient.js';

export type BuildAppOptions = {
  aiDirectorService?: AiDirectorService;
};

const REQUEST_BODY_LIMIT_BYTES = 128 * 1024;

export function buildApp(options: BuildAppOptions = {}): FastifyInstance {
  const app = Fastify({
    bodyLimit: REQUEST_BODY_LIMIT_BYTES,
  });
  const aiDirectorService = options.aiDirectorService ?? createAiDirectorService();

  registerAiRoutes(app, aiDirectorService);

  return app;
}

function createAiDirectorService(): AiDirectorService {
  const env = readEnv();
  const providerClient = new OpenAiCompatibleProviderClient({
    apiKey: env.MODEL_API_KEY,
    modelName: env.MODEL_NAME,
    timeoutMs: env.REQUEST_TIMEOUT_MS,
    baseUrl: env.MODEL_BASE_URL,
  });

  return new DefaultAiDirectorService(providerClient);
}
