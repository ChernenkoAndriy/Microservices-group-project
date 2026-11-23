import { defineConfig, devices } from '@playwright/test';

// Chromium only, headless, one worker (weak machine). Runs against a live app and backend:
// E2E_BASE_URL defaults to the Vite dev server; use http://localhost:3000 for the frontend in the cluster.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  workers: 1,
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
