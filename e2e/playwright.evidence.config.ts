import { defineConfig, devices } from '@playwright/test';

// Evidencia visual (`npm run evidence -w e2e`): solo las pruebas @evidencia de tests/evidence/.
// Guardan capturas en docs/evidence/<cambio>/ para enlazarlas en el PR. Requiere el stack arriba.
export default defineConfig({
  testDir: './tests/evidence',
  grep: /@evidencia/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  outputDir: 'test-results/evidence',
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    locale: 'es-BO',
    timezoneId: 'America/La_Paz',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'evidence',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
  ],
});
