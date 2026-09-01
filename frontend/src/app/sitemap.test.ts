import { describe, it, expect } from 'vitest';
import sitemap from './sitemap';

// phase-7 §8.2, §9 — /concierge and /console stay out of the sitemap; a demo and a login screen
// have no business in search results.
describe('sitemap', () => {
  it('lists exactly the four public marketing routes', () => {
    const entries = sitemap();
    const paths = entries.map((entry) => new URL(entry.url).pathname).sort();

    expect(paths).toEqual(['/', '/about', '/contact', '/product']);
  });
});
