import { type FastifyInstance, type FastifyReply } from 'fastify';
import { ZodError } from 'zod';
import { failureEnvelope, successEnvelope } from '../lib/envelope.js';
import {
  assemblySuggestionSchema,
  buildAssemblyRequestSchema,
  clipReviewSchema,
  directorPlanSchema,
  generateDirectorPlanRequestSchema,
  reviewClipRequestSchema,
} from '../schemas/ai.js';
import {
  type AiDirectorService,
  InvalidAiOutputError,
  UpstreamAiError,
} from '../services/aiDirectorService.js';

export function registerAiRoutes(app: FastifyInstance, aiDirectorService: AiDirectorService): void {
  app.post('/v1/ai/director-plan', async (request, reply) => {
    const requestBody = generateDirectorPlanRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    try {
      const result = await aiDirectorService.generateDirectorPlan(requestBody.data.brief);
      const validated = directorPlanSchema.parse(result);
      return reply.code(200).send(successEnvelope(validated));
    } catch (error) {
      return sendRouteError(reply, error);
    }
  });

  app.post('/v1/ai/clip-review', async (request, reply) => {
    const requestBody = reviewClipRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    try {
      const result = await aiDirectorService.reviewClip(
        requestBody.data.shotTask,
        requestBody.data.attemptNumber,
        requestBody.data.mediaType,
      );
      const validated = clipReviewSchema.parse(result);
      return reply.code(200).send(successEnvelope(validated));
    } catch (error) {
      return sendRouteError(reply, error);
    }
  });

  app.post('/v1/ai/assembly-suggestion', async (request, reply) => {
    const requestBody = buildAssemblyRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    try {
      const result = await aiDirectorService.buildAssembly(requestBody.data.project);
      const validated = assemblySuggestionSchema.parse(result);
      return reply.code(200).send(successEnvelope(validated));
    } catch (error) {
      return sendRouteError(reply, error);
    }
  });
}

function sendRouteError(reply: FastifyReply, error: unknown) {
  if (error instanceof InvalidAiOutputError || error instanceof ZodError) {
    return reply.code(502).send(failureEnvelope('AI 返回的数据格式不正确。'));
  }
  if (error instanceof UpstreamAiError) {
    return reply.code(502).send(failureEnvelope('AI 服务暂时不可用，请稍后重试。'));
  }
  return reply.code(500).send(failureEnvelope('服务暂时不可用，请稍后重试。'));
}
