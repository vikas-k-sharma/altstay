// vitest stub for the `server-only` marker package.
//
// The real package throws unconditionally on import; Next's build resolves it to a no-op via
// the `react-server` package.json export condition when bundling for the server. Vitest doesn't
// set that condition, so tests alias `server-only` to this empty module instead — the enforcement
// itself is Next's bundler's job (see next.config.ts / the build output), not something a unit
// test needs to re-prove.
export {};
