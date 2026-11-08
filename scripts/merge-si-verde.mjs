#!/usr/bin/env node
// Único camino de merge del agente (ADR-004). Uso: node scripts/merge-si-verde.mjs <pr>
// Exige: PR abierto y no borrador, check `ci-ok` en SUCCESS, ningún check en FAILURE,
// etiquetas `revisor:aprobado` y `seguridad:ok` puestas después del último commit.
// Luego: gh pr merge <pr> --squash --delete-branch --match-head-commit <sha>.
import { pathToFileURL } from 'node:url';
import { run, capture } from './lib/run.mjs';

export const LABELS_REQUERIDAS = ['revisor:aprobado', 'seguridad:ok'];
const CAMPOS = 'headRefOid,labels,commits,statusCheckRollup,state,isDraft,title';

function gh(args) {
  return capture('gh', args);
}

function verPr(pr) {
  const r = gh(['pr', 'view', pr, '--json', CAMPOS]);
  if (r.status !== 0) throw new Error(`no se pudo leer el PR #${pr}: ${r.stderr.trim()}`);
  return JSON.parse(r.stdout);
}

const nombreCheck = (c) => c.name ?? c.context ?? '(sin nombre)';
const resultadoCheck = (c) => String(c.conclusion ?? c.state ?? '').toUpperCase();

/** Evalúa estado, checks y etiquetas. Devuelve la lista de motivos de rechazo (vacía = OK). */
export function evaluarPr(data) {
  const motivos = [];
  if (data.state !== 'OPEN') motivos.push(`el PR no está abierto (estado: ${data.state})`);
  if (data.isDraft) motivos.push('el PR es un borrador');

  const checks = data.statusCheckRollup ?? [];
  const ciOk = checks.find((c) => nombreCheck(c) === 'ci-ok');
  if (!ciOk) motivos.push('no existe el check "ci-ok"');
  else if (resultadoCheck(ciOk) !== 'SUCCESS')
    motivos.push(`el check "ci-ok" no está en SUCCESS (${resultadoCheck(ciOk) || 'pendiente'})`);
  const fallidos = checks.filter((c) => ['FAILURE', 'ERROR'].includes(resultadoCheck(c)));
  if (fallidos.length) motivos.push(`hay checks fallidos: ${fallidos.map(nombreCheck).join(', ')}`);

  const labels = new Set((data.labels ?? []).map((l) => l.name));
  for (const l of LABELS_REQUERIDAS) if (!labels.has(l)) motivos.push(`falta la etiqueta "${l}"`);
  return motivos;
}

/** Verifica que la última aplicación de cada etiqueta requerida sea posterior al último commit. */
export function evaluarEtiquetasVsCommit(eventos, ultimoCommitIso) {
  const motivos = [];
  const tCommit = Date.parse(ultimoCommitIso);
  for (const l of LABELS_REQUERIDAS) {
    const fechas = eventos
      .filter((e) => e.event === 'labeled' && e.label?.name === l)
      .map((e) => Date.parse(e.created_at));
    const ultima = fechas.length ? Math.max(...fechas) : NaN;
    if (!(ultima > tCommit))
      motivos.push(
        `la etiqueta "${l}" se puso antes del último commit (${ultimoCommitIso}); revisar de nuevo`,
      );
  }
  return motivos;
}

function rechazar(pr, motivos) {
  console.error(`\nNO se fusiona el PR #${pr}. Motivos:`);
  for (const m of motivos) console.error(`  - ${m}`);
  process.exitCode = 1;
}

export function main(argv = process.argv.slice(2)) {
  const pr = argv[0];
  if (!pr || !/^\d+$/.test(pr)) {
    console.error('Uso: node scripts/merge-si-verde.mjs <número de PR>');
    return 1;
  }

  let data = verPr(pr);
  const previos = [];
  if (data.state !== 'OPEN') previos.push(`el PR no está abierto (estado: ${data.state})`);
  if (data.isDraft) previos.push('el PR es un borrador');
  if (previos.length) return (rechazar(pr, previos), 1);

  console.log(`PR #${pr}: ${data.title}\nEsperando checks…`);
  run('gh', ['pr', 'checks', pr, '--watch', '--interval', '20']); // el código se ignora a propósito

  data = verPr(pr);
  const motivos = evaluarPr(data);

  const commits = data.commits ?? [];
  const ultimo = commits[commits.length - 1];
  const sha = data.headRefOid;
  if (ultimo?.oid && ultimo.oid !== sha)
    motivos.push(`el último commit (${ultimo.oid}) no coincide con headRefOid (${sha})`);

  if (ultimo?.committedDate) {
    const ev = gh([
      'api',
      `repos/{owner}/{repo}/issues/${pr}/events?per_page=100`,
      '--paginate',
      '--slurp',
    ]);
    if (ev.status !== 0) {
      console.warn(
        `AVISO: no se pudieron leer los eventos de etiquetas (${ev.stderr.trim()}); se omite la verificación de orden.`,
      );
    } else {
      try {
        const eventos = JSON.parse(ev.stdout).flat();
        motivos.push(...evaluarEtiquetasVsCommit(eventos, ultimo.committedDate));
      } catch (err) {
        console.warn(
          `AVISO: respuesta de eventos ilegible (${err.message}); se omite la verificación de orden.`,
        );
      }
    }
  } else {
    console.warn(
      'AVISO: no se conoce la fecha del último commit; se omite la verificación de orden.',
    );
  }

  if (motivos.length) return (rechazar(pr, motivos), 1);

  console.log(`Todo en verde. Fusionando PR #${pr} (squash) en ${sha}…`);
  const code = run('gh', [
    'pr',
    'merge',
    pr,
    '--squash',
    '--delete-branch',
    '--match-head-commit',
    sha,
  ]);
  if (code !== 0) {
    console.error(`gh pr merge falló con código ${code}.`);
    return 1;
  }
  console.log(`PR #${pr} fusionado.`);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = main();
}
