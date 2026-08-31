import { describe, it, expect } from 'vitest';
import * as home from './page';
import * as product from './product/page';
import * as about from './about/page';
import * as contact from './contact/page';

// phase-7 §8.2 / §9 — "the easiest thing to forget on a new page." One test per page module so a
// missing export fails immediately rather than being caught by an SEO tool nobody runs locally.
describe('marketing page metadata', () => {
  it.each([
    ['/', home],
    ['/product', product],
    ['/about', about],
    ['/contact', contact],
  ])('%s exports a non-empty title and description', (_route, mod) => {
    const metadata = mod.metadata as { title?: unknown; description?: unknown };
    expect(typeof metadata.title === 'string' ? metadata.title.length > 0 : Boolean(metadata.title)).toBe(true);
    expect(metadata.description).toBeTruthy();
  });

  it.each([
    ['/', home],
    ['/product', product],
    ['/about', about],
    ['/contact', contact],
  ])('%s is force-static', (_route, mod) => {
    expect(mod.dynamic).toBe('force-static');
  });
});
