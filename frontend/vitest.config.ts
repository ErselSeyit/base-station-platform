import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  plugins: [react()] as any,
  test: {
    environment: 'jsdom',
    exclude: ['**/node_modules/**', '**/e2e/**', '**/playwright-report/**'],
    globals: true,
    setupFiles: './src/test/setup.ts',
    // userEvent simulates per-keystroke input, so form-heavy tests are slow on
    // shared CI runners; 5s (the default) flakes there. 15s gives headroom
    // without letting a genuinely hung test run forever.
    testTimeout: 15000,
  },
})

