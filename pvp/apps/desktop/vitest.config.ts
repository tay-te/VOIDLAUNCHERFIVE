import { defineConfig } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  resolve: {
    alias: [
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      // Stores are tested against the same mock the browser dev server uses, so a
      // green test means the reviewable build works too.
      {
        find: /^@tauri-apps\/api\/(core|event)$/,
        replacement: fileURLToPath(new URL('./src/mocks/tauri.ts', import.meta.url)),
      },
    ],
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    globals: false,
  },
});
