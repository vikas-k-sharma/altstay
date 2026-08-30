import { z } from 'zod';

// Mirrors com.altstay.api.knowledgebase.dto.KnowledgeBaseVersionResponse /
// SaveKnowledgeBaseRequest. No nullable fields — authoredBy "is derived from the authenticated
// principal and is never null" (the service's own javadoc), and every other field is populated
// whenever a version exists at all.
export const KnowledgeBaseVersionResponseSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  knowledgeBaseId: z.string().uuid(),
  versionNo: z.number().int(),
  content: z.string(),
  contentSha256: z.string(),
  charCount: z.number().int(),
  authoredBy: z.string().uuid(),
  createdAt: z.string().datetime({ offset: true }),
});
export type KnowledgeBaseVersionResponse = z.infer<typeof KnowledgeBaseVersionResponseSchema>;

// The 20,000-character limit exists in three places already (`@Size`, the `char_count` check
// constraint, and the anonymous admin panel) — this is the fourth, and the console must not
// silently accept more only to have the backend reject it (phase-6 §4.10).
export const SaveKnowledgeBaseRequestSchema = z.object({
  content: z.string().min(1, 'content is required').max(20_000, 'content must not exceed 20000 characters'),
});
export type SaveKnowledgeBaseRequest = z.infer<typeof SaveKnowledgeBaseRequestSchema>;
