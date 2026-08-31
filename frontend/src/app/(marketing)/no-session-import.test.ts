import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

// phase-7 §4.2 / §9 — a stray import of the session module silently opts a static marketing
// route into dynamic rendering (CLAUDE.md's CSRF/BFF note is the same trap one layer over).
// Mechanical, not a read-through: walk every source file under (marketing) and grep the import.
function collectSourceFiles(dir: string): string[] {
  const entries = readdirSync(dir);
  return entries.flatMap((entry) => {
    const full = join(dir, entry);
    const stats = statSync(full);
    if (stats.isDirectory()) return collectSourceFiles(full);
    if (/\.(tsx?|jsx?)$/.test(entry) && !entry.endsWith('.test.ts') && !entry.endsWith('.test.tsx')) {
      return [full];
    }
    return [];
  });
}

describe('marketing route group', () => {
  it('never imports the session module', () => {
    const dir = join(__dirname);
    const files = collectSourceFiles(dir);
    expect(files.length).toBeGreaterThan(0);

    const offenders = files.filter((file) => readFileSync(file, 'utf-8').includes("lib/server/session"));
    expect(offenders).toEqual([]);
  });
});
