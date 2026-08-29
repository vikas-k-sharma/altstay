import { z } from 'zod';

export const RoleSchema = z.enum(['USER', 'ASSISTANT']);
export type Role = z.infer<typeof RoleSchema>;

export const ChatTurnSchema = z.object({
  role: RoleSchema,
  content: z.string().max(4000),
});
export type ChatTurn = z.infer<typeof ChatTurnSchema>;

export const ChatRequestSchema = z.object({
  propertyName: z.string().min(1).default('AltStay Property'),
  knowledgeBase: z.string().min(1, 'Knowledge base cannot be empty').max(20000, 'Knowledge base must be under 20,000 characters'),
  history: z.array(ChatTurnSchema).max(200).default([]),
  message: z.string().min(1, 'Message cannot be empty').max(1000, 'Message must be under 1,000 characters'),
});
export type ChatRequest = z.infer<typeof ChatRequestSchema>;

export const TokenUsageSchema = z.object({
  promptTokens: z.number().int().nonnegative().default(0),
  completionTokens: z.number().int().nonnegative().default(0),
  totalTokens: z.number().int().nonnegative().default(0),
});
export type TokenUsage = z.infer<typeof TokenUsageSchema>;

export const ChatResponseSchema = z.object({
  reply: z.string(),
  escalated: z.boolean().default(false),
  model: z.string().default('gemini-2.5-flash'),
  usage: TokenUsageSchema,
  latencyMs: z.number().nonnegative().default(0),
});
export type ChatResponse = z.infer<typeof ChatResponseSchema>;

export const ProblemDetailSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  errors: z.record(z.string(), z.string()).optional(),
});
export type ProblemDetail = z.infer<typeof ProblemDetailSchema>;
