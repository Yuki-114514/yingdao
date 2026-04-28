import { randomUUID } from 'node:crypto';

export type AiJobStatus = 'Pending' | 'Succeeded' | 'Failed';

export type PublicAiJob = {
  jobId: string;
  status: AiJobStatus;
  data: unknown | null;
  error: string | null;
};

type AiJobRecord = PublicAiJob & {
  createdAt: number;
  updatedAt: number;
  expiresAt: number;
};

export type AiJobStoreOptions = {
  ttlMs?: number;
  maxJobs?: number;
};

const DEFAULT_JOB_TTL_MS = 30 * 60 * 1000;
const DEFAULT_MAX_JOBS = 200;

export class InMemoryAiJobStore {
  private jobs: ReadonlyMap<string, AiJobRecord> = new Map();

  private readonly ttlMs: number;

  private readonly maxJobs: number;

  constructor(options: AiJobStoreOptions = {}) {
    this.ttlMs = options.ttlMs ?? DEFAULT_JOB_TTL_MS;
    this.maxJobs = options.maxJobs ?? DEFAULT_MAX_JOBS;
  }

  start<T>(
    runOperation: () => Promise<T>,
    validate: (result: T) => T,
    toUserMessage: (error: unknown) => string,
  ): PublicAiJob {
    const now = Date.now();
    const job = {
      jobId: randomUUID(),
      status: 'Pending' as const,
      data: null,
      error: null,
      createdAt: now,
      updatedAt: now,
      expiresAt: now + this.ttlMs,
    };

    this.storeJob(job);
    void this.complete(job.jobId, runOperation, validate, toUserMessage);

    return toPublicJob(job);
  }

  get(jobId: string): PublicAiJob | undefined {
    this.cleanupExpired(Date.now());
    const job = this.jobs.get(jobId);
    return job ? toPublicJob(job) : undefined;
  }

  private async complete<T>(
    jobId: string,
    runOperation: () => Promise<T>,
    validate: (result: T) => T,
    toUserMessage: (error: unknown) => string,
  ): Promise<void> {
    try {
      const result = await runOperation();
      const validated = validate(result);
      this.updateJob(jobId, (job, now) => ({
        ...job,
        status: 'Succeeded',
        data: validated,
        error: null,
        updatedAt: now,
        expiresAt: now + this.ttlMs,
      }));
    } catch (error) {
      this.updateJob(jobId, (job, now) => ({
        ...job,
        status: 'Failed',
        data: null,
        error: toUserMessage(error),
        updatedAt: now,
        expiresAt: now + this.ttlMs,
      }));
    }
  }

  private updateJob(
    jobId: string,
    update: (job: AiJobRecord, now: number) => AiJobRecord,
  ): void {
    const job = this.jobs.get(jobId);
    if (!job) {
      return;
    }

    this.storeJob(update(job, Date.now()));
  }

  private storeJob(job: AiJobRecord): void {
    const now = Date.now();
    const activeJobs = [...this.jobs.values()].filter((candidate) => candidate.expiresAt > now);
    const nextJobs = new Map(activeJobs.map((candidate) => [candidate.jobId, candidate]));
    nextJobs.set(job.jobId, job);

    const newestJobs = [...nextJobs.values()]
      .sort((left, right) => left.createdAt - right.createdAt)
      .slice(-this.maxJobs);

    this.jobs = new Map(newestJobs.map((candidate) => [candidate.jobId, candidate]));
  }

  private cleanupExpired(now: number): void {
    this.jobs = new Map(
      [...this.jobs.values()]
        .filter((job) => job.expiresAt > now)
        .map((job) => [job.jobId, job]),
    );
  }
}

function toPublicJob(job: AiJobRecord): PublicAiJob {
  return {
    jobId: job.jobId,
    status: job.status,
    data: job.data,
    error: job.error,
  };
}
