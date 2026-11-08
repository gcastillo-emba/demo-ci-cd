import { defineConfig, devices } from '@playwright/test';

// E2E contra el stack real (`docker compose up --build --wait`). BASE_URL permite apuntar a otro host.
const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: './tests',
  // La evidencia visual (@evidencia) no corre por defecto: ver playwright.evidence.config.ts.
  // UN login por rol y storageState en e2e/.auth/ (rate limiting del login en nginx, ADR-006).
  globalSetup: './global-setup.ts',
  testIgnore: ['**/evidence/**', '**/support/**'],
  grepInvert: /@evidencia/,
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  workers: isCI ? 1 : undefined,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    locale: 'es-BO',
    timezoneId: 'America/La_Paz',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
