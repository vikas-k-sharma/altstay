import { describe, it, expect } from 'vitest';
import * as home from './opengraph-image';
import * as product from './product/opengraph-image';
import * as about from './about/opengraph-image';
import * as contact from './contact/opengraph-image';

// phase-7 §8.2 — "one OG image per page, generated at build time with next/og." A satori render
// isn't practical to execute under jsdom, so this pins the config exports next/og reads —
// the same mechanical check as the metadata test, one layer over.
describe('marketing OG images', () => {
  it.each([
    ['/', home],
    ['/product', product],
    ['/about', about],
    ['/contact', contact],
  ])('%s exports alt text, a 1200x630 size, and a default generator function', (_route, mod) => {
    expect(mod.alt).toBeTruthy();
    expect(mod.size).toEqual({ width: 1200, height: 630 });
    expect(mod.contentType).toBe('image/png');
    expect(typeof mod.default).toBe('function');
  });
});
