import { proxy } from '@/lib/server/proxy';
import { SaveKnowledgeBaseRequestSchema, KnowledgeBaseVersionResponseSchema } from '@/lib/contracts/knowledgeBase';

// The one BFF route in the console keyed by property id rather than slug (phase-6 §3.1, §4.10).
export const POST = proxy({
  method: 'POST',
  path: (req) => `/api/v1/properties/${req.nextUrl.pathname.split('/').at(-1)}/knowledge-base`,
  requestSchema: SaveKnowledgeBaseRequestSchema,
  responseSchema: KnowledgeBaseVersionResponseSchema,
});
