import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      { find: '@void-ui-src', replacement: resolve(here, '../ui/src') },
      { find: '@void/ui/tokens.css', replacement: resolve(here, '../ui/src/tokens.css') },
      { find: '@void/ui/fonts.css', replacement: resolve(here, '../ui/src/fonts.css') },
      { find: '@void/ui/styles.css', replacement: resolve(here, 'src/styles/void-ui.css') },
      { find: /^@void\/ui$/, replacement: resolve(here, '../ui/src/index.ts') },
      { find: /^@void\/protocol$/, replacement: resolve(here, '../protocol/src/index.ts') },
      { find: '@', replacement: resolve(here, 'src') },
    ],
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['test/**/*.test.{ts,tsx}'],
    setupFiles: ['test/setup.ts'],
  },
});
