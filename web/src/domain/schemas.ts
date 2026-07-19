import { z } from 'zod';

const nullableString = z.union([z.string(), z.null()]);
const nullableNumber = z.union([z.number(), z.null()]);

export const medicationPayloadSchema = z.object({
  id: z.string().min(1),
  ownerProfileId: z.string().min(1),
  name: z.string().min(1),
  doseText: z.string(),
  doseUnit: z.string(),
  foodRelation: z.enum(['BEFORE_FOOD', 'AFTER_FOOD', 'WITH_FOOD', 'NONE']),
  scheduleMode: z.enum(['FIXED_TIMES', 'EVERY_X_HOURS', 'EVERY_X_DAYS']),
  intervalHours: z.number().int().positive().nullable(),
  windowStartTime: nullableString,
  windowEndTime: nullableString,
  startDate: z.string().min(1),
  endDate: nullableString,
  notes: z.string(),
  isActive: z.boolean(),
  createdAtEpochMs: z.number().int().nonnegative(),
  updatedAtEpochMs: z.number().int().nonnegative(),
  weekdays: z.array(z.number().int().min(1).max(7)),
  times: z.array(
    z.object({
      localTime: z.string(),
      sortOrder: z.number().int().nonnegative(),
    })
  ),
});

export const doseEventPayloadSchema = z.object({
  id: z.string().min(1),
  ownerProfileId: z.string().min(1),
  medicationId: z.string().min(1),
  medicationName: z.string().min(1),
  scheduledEpochMs: z.number().int().nonnegative(),
  localDate: z.string().min(1),
  localTime: z.string().min(1),
  status: z.enum(['PENDING', 'MISSED', 'TAKEN']),
  actionSource: z.union([z.enum(['APP', 'NOTIFICATION', 'SYSTEM']), z.null()]).optional().default(null),
  recordSource: z.union([z.enum(['mobile', 'web']), z.null()]).optional().default(null),
  reminderAttemptCount: z.number().int().nonnegative().optional().default(0),
  snoozeUntilEpochMs: nullableNumber.optional().default(null),
  respondedEpochMs: nullableNumber.optional().default(null),
  createdAtEpochMs: z.number().int().nonnegative(),
  updatedAtEpochMs: z.number().int().nonnegative(),
});

export const hiddenIntakeLogPayloadSchema = z.object({
  id: z.string().min(1),
  ownerProfileId: z.string().min(1),
  doseEventId: nullableString,
  medicationId: z.string().min(1),
  medicationName: z.string().min(1),
  operationType: z.enum(['ADD', 'UPDATE', 'DELETE', 'CHANGE']),
  operationDetails: nullableString,
  actionSource: z.enum(['APP', 'NOTIFICATION', 'SYSTEM']),
  takenAtEpochMs: z.number().int().nonnegative(),
  createdAtEpochMs: z.number().int().nonnegative(),
});

export const cloudRecordSchema = z.object({
  recordType: z.enum(['MEDICATION', 'DOSE_EVENT', 'HIDDEN_INTAKE_LOG']),
  recordId: z.string().min(1),
  updatedAt: z.number().int().nonnegative(),
  deletedAt: nullableNumber.optional().default(null),
  source: z.union([z.enum(['mobile', 'web']), z.null()]).optional().default(null),
  iv: nullableString.optional().default(null),
  ciphertext: nullableString.optional().default(null),
});

export const cloudRecordDocumentSchema = cloudRecordSchema.extend({
  source: z.enum(['mobile', 'web']),
  schemaVersion: z.number().int().positive(),
});

export const syncCodeSchema = z.string().trim().min(8);
