import { z } from 'zod';

const shortTextSchema = z.string().min(1).max(120);
const mediumTextSchema = z.string().min(1).max(240);
const longTextSchema = z.string().min(1).max(600);
const optionalShortTextSchema = z.string().max(120);
const optionalMediumTextSchema = z.string().max(240);
const optionalLongTextSchema = z.string().max(600);
const clipPathSchema = z.string().min(1).max(500);
const scoreSchema = z.number().int().min(0).max(100);
const capturedMediaSchema = z.object({
  mimeType: z.enum(['image/jpeg', 'image/png']),
  dataBase64: z.string().min(1).max(1_200_000),
});

export const projectStatusSchema = z.enum([
  'Draft',
  'BriefReady',
  'ShotPlanReady',
  'Shooting',
  'ReviewReady',
  'AssemblyReady',
]);

export const shotStatusSchema = z.enum([
  'Planned',
  'Active',
  'Captured',
  'Approved',
  'RetakeSuggested',
  'Skipped',
]);

export const timePressureSchema = z.enum(['Low', 'Medium', 'High']);
export const retakePrioritySchema = z.enum(['Low', 'Medium', 'High']);
export const mediaTypeSchema = z.enum(['Photo', 'Video']);

export const creativeBriefSchema = z.object({
  title: mediumTextSchema,
  theme: mediumTextSchema,
  style: shortTextSchema,
  durationSec: z.number().int().min(5).max(600),
  castCount: z.number().int().min(1).max(20),
  locations: z.array(shortTextSchema).min(1).max(10),
  needCaption: z.boolean(),
  needVoiceover: z.boolean(),
  shootGoal: longTextSchema,
  mood: shortTextSchema,
  highlightSubject: mediumTextSchema,
  soloMode: z.boolean(),
  timePressure: timePressureSchema,
  mediaType: mediaTypeSchema.optional(),
});

export const clipReviewSchema = z.object({
  clipId: z.string().max(80),
  usable: z.boolean(),
  score: scoreSchema,
  issues: z.array(optionalMediumTextSchema).max(6),
  suggestion: optionalLongTextSchema,
  stabilityScore: scoreSchema,
  subjectScore: scoreSchema,
  compositionScore: scoreSchema,
  emotionScore: scoreSchema,
  keepReason: optionalMediumTextSchema,
  retakeReason: optionalMediumTextSchema,
  nextAction: optionalMediumTextSchema,
});

export const shotTaskSchema: z.ZodType<{
  id: string;
  orderIndex: number;
  title: string;
  goal: string;
  shotType: string;
  durationSuggestSec: number;
  compositionHint: string;
  actionHint: string;
  status: z.infer<typeof shotStatusSchema>;
  capturedClipIds: string[];
  latestReview: z.infer<typeof clipReviewSchema> | null;
  beatLabel: string;
  whyThisShotMatters: string;
  successChecklist: string[];
  difficultyHint: string;
  retakePriority: z.infer<typeof retakePrioritySchema>;
}> = z.lazy(() =>
  z.object({
    id: z.string().min(1).max(80),
    orderIndex: z.number().int().min(1).max(100),
    title: mediumTextSchema,
    goal: longTextSchema,
    shotType: shortTextSchema,
    durationSuggestSec: z.number().int().min(1).max(60),
    compositionHint: mediumTextSchema,
    actionHint: mediumTextSchema,
    status: shotStatusSchema,
    capturedClipIds: z.array(z.string().min(1).max(80)).max(20),
    latestReview: clipReviewSchema.nullable(),
    beatLabel: shortTextSchema,
    whyThisShotMatters: mediumTextSchema,
    successChecklist: z.array(shortTextSchema).max(8),
    difficultyHint: mediumTextSchema,
    retakePriority: retakePrioritySchema,
  }),
);

const inboundShotTaskSchema = z.preprocess((value) => {
  if (typeof value !== 'object' || value === null) {
    return value;
  }

  const candidate = value as Record<string, unknown>;
  return {
    ...candidate,
    latestReview: candidate.latestReview ?? null,
    capturedClipIds: Array.isArray(candidate.capturedClipIds) ? candidate.capturedClipIds : [],
    status: candidate.status ?? 'Planned',
    retakePriority: candidate.retakePriority ?? 'Medium',
  };
}, shotTaskSchema);

export const directorPlanSchema = z.object({
  title: mediumTextSchema,
  storyLogline: longTextSchema,
  beatSummary: z.array(shortTextSchema).min(1).max(8),
  shotTasks: z.array(shotTaskSchema).min(1).max(6),
});

const baseClipAssetSchema = z.object({
  id: z.string().min(1).max(80),
  shotTaskId: z.string().min(1).max(80),
  localPath: clipPathSchema,
  durationSec: z.number().min(0).max(600),
  thumbnailLabel: shortTextSchema,
  mediaType: mediaTypeSchema.optional(),
  review: clipReviewSchema.nullable(),
});

export const clipAssetSchema = baseClipAssetSchema.refine(
  (clip) => clip.mediaType === 'Photo' || clip.durationSec > 0,
  {
    message: 'Video clips must have a positive duration.',
    path: ['durationSec'],
  },
);

export const assemblySuggestionSchema = z.object({
  orderedClipIds: z.array(z.string().min(1).max(80)).max(20),
  missingShotIds: z.array(z.string().min(1).max(80)).max(12),
  titleOptions: z.array(mediumTextSchema).min(1).max(5),
  captionDraft: z.array(mediumTextSchema).max(12),
  missingBeatLabels: z.array(shortTextSchema).max(12),
  editingDirection: longTextSchema,
  selectionReasonByClipId: z.record(z.string().min(1).max(80), mediumTextSchema),
});

export const projectSchema: z.ZodType<{
  id: string;
  title: string;
  templateId: string;
  status: z.infer<typeof projectStatusSchema>;
  brief: z.infer<typeof creativeBriefSchema>;
  directorPlan: z.infer<typeof directorPlanSchema> | null;
  clips: z.infer<typeof clipAssetSchema>[];
  assemblySuggestion: z.infer<typeof assemblySuggestionSchema> | null;
}> = z.lazy(() =>
  z.object({
    id: z.string().min(1).max(80),
    title: mediumTextSchema,
    templateId: z.string().min(1).max(80),
    status: projectStatusSchema,
    brief: creativeBriefSchema,
    directorPlan: directorPlanSchema.nullable(),
    clips: z.array(clipAssetSchema).max(24),
    assemblySuggestion: assemblySuggestionSchema.nullable(),
  }),
);

const inboundProjectSchema = z.preprocess((value) => {
  if (typeof value !== 'object' || value === null) {
    return value;
  }

  const candidate = value as Record<string, unknown>;
  const directorPlan = typeof candidate.directorPlan === 'object' && candidate.directorPlan !== null
    ? {
        ...(candidate.directorPlan as Record<string, unknown>),
        shotTasks: Array.isArray((candidate.directorPlan as Record<string, unknown>).shotTasks)
          ? ((candidate.directorPlan as Record<string, unknown>).shotTasks as unknown[]).map((shotTask) =>
              typeof shotTask === 'object' && shotTask !== null
                ? { latestReview: null, ...(shotTask as Record<string, unknown>) }
                : shotTask,
            )
          : (candidate.directorPlan as Record<string, unknown>).shotTasks,
      }
    : candidate.directorPlan;

  return {
    ...candidate,
    directorPlan,
    clips: Array.isArray(candidate.clips)
      ? candidate.clips.map((clip) =>
          typeof clip === 'object' && clip !== null
            ? { review: null, ...(clip as Record<string, unknown>) }
            : clip,
        )
      : candidate.clips,
    assemblySuggestion: candidate.assemblySuggestion ?? null,
  };
}, projectSchema);

export const generateDirectorPlanRequestSchema = z.object({
  brief: creativeBriefSchema,
});

export const reviewClipRequestSchema = z.object({
  shotTask: inboundShotTaskSchema,
  attemptNumber: z.number().int().min(1).max(10),
  mediaType: mediaTypeSchema.optional(),
  capturedMedia: capturedMediaSchema.optional(),
});

export const buildAssemblyRequestSchema = z.object({
  project: inboundProjectSchema,
});

export type CreativeBrief = z.infer<typeof creativeBriefSchema>;
export type DirectorPlan = z.infer<typeof directorPlanSchema>;
export type ShotTask = z.infer<typeof shotTaskSchema>;
export type ClipReview = z.infer<typeof clipReviewSchema>;
export type ClipAsset = z.infer<typeof clipAssetSchema>;
export type AssemblySuggestion = z.infer<typeof assemblySuggestionSchema>;
export type Project = z.infer<typeof projectSchema>;
export type GenerateDirectorPlanRequest = z.infer<typeof generateDirectorPlanRequestSchema>;
export type ReviewClipRequest = z.infer<typeof reviewClipRequestSchema>;
export type BuildAssemblyRequest = z.infer<typeof buildAssemblyRequestSchema>;
