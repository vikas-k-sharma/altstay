import { describe, it, expect } from 'vitest';
import { KnowledgeBaseVersionResponseSchema, SaveKnowledgeBaseRequestSchema } from './knowledgeBase';
import versionFixture from './__fixtures__/knowledge-base-version.json';

describe('KnowledgeBaseVersionResponseSchema', () => {
  it('parses a recorded version', () => {
    const parsed = KnowledgeBaseVersionResponseSchema.parse(versionFixture);
    expect(parsed.versionNo).toBe(3);
    expect(parsed.charCount).toBe(61);
  });
});

describe('SaveKnowledgeBaseRequestSchema', () => {
  it('rejects content over the 20,000-character limit', () => {
    const parsed = SaveKnowledgeBaseRequestSchema.safeParse({ content: 'x'.repeat(20_001) });
    expect(parsed.success).toBe(false);
  });

  it('rejects empty content', () => {
    const parsed = SaveKnowledgeBaseRequestSchema.safeParse({ content: '' });
    expect(parsed.success).toBe(false);
  });
});
