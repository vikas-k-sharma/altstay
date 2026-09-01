import { describe, it, expect } from 'vitest';
import robots from './robots';

// phase-7 §8.2 — /concierge and /console are noindex; robots.txt keeps crawlers out of both.
describe('robots', () => {
  it('disallows /concierge and /console and points at the sitemap', () => {
    const result = robots();
    const rule = Array.isArray(result.rules) ? result.rules[0] : result.rules;
    const disallow = Array.isArray(rule.disallow) ? rule.disallow : [rule.disallow];

    expect(disallow).toContain('/concierge');
    expect(disallow).toContain('/console');
    expect(result.sitemap).toBeTruthy();
  });
});
