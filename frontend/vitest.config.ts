import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      // Next resolves `server-only` to a no-op via the `react-server` export condition when
      // bundling for the server; vitest doesn't set that condition, so without this alias every
      // test that imports session.ts (or anything importing it) would throw on import. See
      // src/lib/server/__mocks__/server-only.ts.
      'server-only': path.resolve(__dirname, './src/lib/server/__mocks__/server-only.ts'),
    },
  },
});
