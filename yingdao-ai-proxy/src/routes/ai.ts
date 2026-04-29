import { type FastifyInstance, type FastifyReply } from 'fastify';
import { z, ZodError } from 'zod';
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
import { InMemoryAiJobStore, type PublicAiJob } from '../services/aiJobStore.js';

const AI_RESPONSE_HEARTBEAT_INTERVAL_MS = 25000;
const AI_RESPONSE_HEARTBEAT_CHUNK = '\n'.repeat(2048);
const aiJobParamsSchema = z.object({
  jobId: z.string().min(1).max(120),
});

export function registerAiRoutes(app: FastifyInstance, aiDirectorService: AiDirectorService): void {
  const aiJobStore = new InMemoryAiJobStore();

  app.post('/v1/ai/jobs/director-plan', async (request, reply) => {
    const requestBody = generateDirectorPlanRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    const job = aiJobStore.start(
      async () => aiDirectorService.generateDirectorPlan(requestBody.data.brief),
      (result) => directorPlanSchema.parse(result),
      toJobErrorMessage,
    );

    return reply.code(202).send(successEnvelope(toStartJobResponse(job)));
  });

  app.post('/v1/ai/jobs/clip-review', async (request, reply) => {
    const requestBody = reviewClipRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    const job = aiJobStore.start(
      async () => aiDirectorService.reviewClip(
        requestBody.data.shotTask,
        requestBody.data.attemptNumber,
        requestBody.data.mediaType,
        requestBody.data.capturedMedia,
      ),
      (result) => clipReviewSchema.parse(result),
      toJobErrorMessage,
    );

    return reply.code(202).send(successEnvelope(toStartJobResponse(job)));
  });

  app.post('/v1/ai/jobs/assembly-suggestion', async (request, reply) => {
    const requestBody = buildAssemblyRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    const job = aiJobStore.start(
      async () => aiDirectorService.buildAssembly(requestBody.data.project),
      (result) => assemblySuggestionSchema.parse(result),
      toJobErrorMessage,
    );

    return reply.code(202).send(successEnvelope(toStartJobResponse(job)));
  });

  app.get('/v1/ai/jobs/:jobId', async (request, reply) => {
    const requestParams = aiJobParamsSchema.safeParse(request.params);
    if (!requestParams.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    const job = aiJobStore.get(requestParams.data.jobId);
    if (!job) {
      return reply.code(404).send(failureEnvelope('任务不存在或已过期。'));
    }

    return reply.send(successEnvelope(job));
  });

  app.post('/v1/ai/director-plan', async (request, reply) => {
    const requestBody = generateDirectorPlanRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    return sendAiResponseWithHeartbeat(
      reply,
      async () => aiDirectorService.generateDirectorPlan(requestBody.data.brief),
      (result) => directorPlanSchema.parse(result),
    );
  });

  app.post('/v1/ai/clip-review', async (request, reply) => {
    const requestBody = reviewClipRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    return sendAiResponseWithHeartbeat(
      reply,
      async () => aiDirectorService.reviewClip(
        requestBody.data.shotTask,
        requestBody.data.attemptNumber,
        requestBody.data.mediaType,
        requestBody.data.capturedMedia,
      ),
      (result) => clipReviewSchema.parse(result),
    );
  });

  app.post('/v1/ai/assembly-suggestion', async (request, reply) => {
    const requestBody = buildAssemblyRequestSchema.safeParse(request.body);
    if (!requestBody.success) {
      return reply.code(400).send(failureEnvelope('请求参数不合法。'));
    }

    return sendAiResponseWithHeartbeat(
      reply,
      async () => aiDirectorService.buildAssembly(requestBody.data.project),
      (result) => assemblySuggestionSchema.parse(result),
    );
  });
}

function toStartJobResponse(job: PublicAiJob) {
  return {
    jobId: job.jobId,
    status: job.status,
  };
}

async function sendAiResponseWithHeartbeat<T>(
  reply: FastifyReply,
  runOperation: () => Promise<T>,
  validate: (result: T) => T,
) {
  let isStreaming = false;
  reply.hijack();

  const startStreaming = () => {
    if (isStreaming || reply.raw.destroyed) {
      return;
    }

    isStreaming = true;
    reply.raw.statusCode = 200;
    reply.raw.setHeader('Content-Type', 'application/json; charset=utf-8');
    reply.raw.flushHeaders();
  };

  const heartbeat = setInterval(() => {
    startStreaming();
    if (!reply.raw.destroyed) {
      reply.raw.write(AI_RESPONSE_HEARTBEAT_CHUNK);
    }
  }, AI_RESPONSE_HEARTBEAT_INTERVAL_MS);

  try {
    const result = await runOperation();
    const validated = validate(result);
    sendJson(reply, isStreaming, 200, successEnvelope(validated));
  } catch (error) {
    const routeError = getRouteError(error);
    sendJson(reply, isStreaming, routeError.statusCode, routeError.body);
  } finally {
    clearInterval(heartbeat);
  }
}

function sendJson(
  reply: FastifyReply,
  isStreaming: boolean,
  statusCode: number,
  body: ReturnType<typeof successEnvelope> | ReturnType<typeof failureEnvelope>,
) {
  const json = JSON.stringify(body);
  if (isStreaming) {
    reply.raw.end(json);
    return;
  }

  reply.raw.statusCode = statusCode;
  reply.raw.setHeader('Content-Type', 'application/json; charset=utf-8');
  reply.raw.end(json);
}

function sendRouteError(reply: FastifyReply, error: unknown) {
  const routeError = getRouteError(error);
  return reply.code(routeError.statusCode).send(routeError.body);
}

function toJobErrorMessage(error: unknown): string {
  return getRouteError(error).body.error ?? '服务暂时不可用，请稍后重试。';
}

function getRouteError(error: unknown) {
  if (error instanceof InvalidAiOutputError || error instanceof ZodError) {
    return {
      statusCode: 502,
      body: failureEnvelope('AI 返回的数据格式不正确。'),
    };
  }
  if (error instanceof UpstreamAiError) {
    return {
      statusCode: 502,
      body: failureEnvelope('AI 服务暂时不可用，请稍后重试。'),
    };
  }
  return {
    statusCode: 500,
    body: failureEnvelope('服务暂时不可用，请稍后重试。'),
  };
}
