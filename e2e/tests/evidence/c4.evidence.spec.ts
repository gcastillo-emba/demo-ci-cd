import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Page } from '@playwright/test';
import { newEntryForm, requestSuggestion, suggestionPanel } from '../support/assistant';
import { ABOGADO_STATE, expect, rolePage, SOCIA_STATE, test } from '../support/auth';
import { createClient, createMatter, runId } from '../support/session';
import { isolatedWorkDate } from '../support/time-entries';

// Evidencia visual de C4 (tarea 4.4). Fuera de la corrida por defecto: solo con
// `npm run evidence -w e2e` (playwright.evidence.config.ts). Capturas en
// docs/evidence/add-description-assistant/. Sesiones por rol desde el storageState del global setup.
const OUT_DIR = fileURLToPath(
  new URL('../../../docs/evidence/add-description-assistant/', import.meta.url),
);

async function capture(page: Page, name: string) {
  mkdirSync(OUT_DIR, { recursive: true });
  await page.screenshot({ path: `${OUT_DIR}${name}.png`, fullPage: true });
}

test('evidencia C4: sugerencia, edición, registro guardado, descarte y no disponible @evidencia', async ({
  browser,
}) => {
  const id = runId();
  const workDate = isolatedWorkDate();
  const socia = await rolePage(browser, SOCIA_STATE);
  const abogado = await rolePage(browser, ABOGADO_STATE);

  await socia.goto('/');
  const clientName = `Cliente Evidencia Asistente ${id}`;
  await createClient(socia, clientName, `EVA-${id}`);
  const title = `Asunto de evidencia del asistente ${id}`;
  const code = await createMatter(socia, clientName, title);

  // 01 — sugerencia mostrada (proveedor fake del perfil demo) como texto plano.
  await abogado.goto('/registrar-horas');
  const form = newEntryForm(abogado);
  await form
    .getByRole('combobox', { name: 'Asunto' })
    .selectOption({ label: `${code} — ${title}` });
  await form.getByLabel('Fecha de trabajo').fill(workDate);
  await form.getByRole('textbox', { name: 'Horas' }).fill('1,5');
  const suggestion = await requestSuggestion(
    abogado,
    'reunion cliente revisar contrato arrendamiento',
  );
  await capture(abogado, '01-sugerencia');

  // 02 — "Editar" copia la sugerencia al campo y el Abogado la completa.
  await suggestionPanel(abogado).getByRole('button', { name: 'Editar' }).click();
  const description = form.getByRole('textbox', { name: 'Descripción' });
  await expect(description).toBeFocused();
  await abogado.keyboard.type(' Incluye observaciones a la cláusula de renovación.');
  await expect(description).toHaveValue(
    `${suggestion} Incluye observaciones a la cláusula de renovación.`,
  );
  await capture(abogado, '02-sugerencia-editada');

  // 03 — el registro solo se guarda al enviar el formulario.
  await form.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(abogado.getByRole('status')).toContainText('Registro guardado: 1,5 h');
  await capture(abogado, '03-registro-guardado');

  // 04 — "Descartar" conserva el texto original.
  await requestSuggestion(abogado, 'llamada cliente redaccion demanda');
  await suggestionPanel(abogado).getByRole('button', { name: 'Descartar' }).click();
  await expect(description).toHaveValue('llamada cliente redaccion demanda');
  await capture(abogado, '04-sugerencia-descartada');

  // 05 — 503 assistant-unavailable. El fake del perfil demo no falla: la respuesta se simula en el
  // navegador solo para mostrar el mensaje en español de la interfaz.
  await abogado.route('**/api/assistant/description-suggestions', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      json: {
        type: 'https://asuntos-horas.example/problems/assistant-unavailable',
        title: 'Asistente no disponible',
        status: 503,
        detail:
          'El asistente no está disponible en este momento; puedes escribir la descripción manualmente.',
        code: 'assistant-unavailable',
        traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
      },
    }),
  );
  await form.getByRole('button', { name: 'Sugerir descripción' }).click();
  await expect(abogado.getByRole('alert')).toContainText('El asistente no está disponible');
  await expect(description).toHaveValue('llamada cliente redaccion demanda');
  await capture(abogado, '05-error-no-disponible');

  await socia.context().close();
  await abogado.context().close();
});
