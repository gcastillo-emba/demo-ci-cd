#!/usr/bin/env node
// Único camino de merge del agente (ADR-004). Uso: node scripts/merge-si-verde.mjs <pr>
// Exige: PR abierto y no borrador, check `ci-ok` en SUCCESS, ningún check en FAILURE,
// etiquetas `revisor:aprobado` y `seguridad:ok` puestas DESPUÉS del inicio del check `ci-ok`
// del commit de cabecera (marca de tiempo registrada por GitHub, no la fecha del commit, que
// controla quien commitea) y un comentario "VEREDICTO: APROBADO" que cite el SHA de cabecera.
// FAIL-CLOSED: si falta la referencia temporal, la API de eventos falla o su respuesta es
// ilegible, se rechaza (exit 1). Nunca se avisa y se continúa.
// Luego: gh pr merge <pr> --squash --delete-branch --match-head-commit <sha>.
import { pathToFileURL } from 'node:url';
import { run, capture } from './lib/run.mjs';

export const LABELS_REQUERIDAS = ['revisor:aprobado', 'seguridad:ok'];
export const CHECK_REFERENCIA = 'ci-ok';
export const MARCA_VEREDICTO = 'VEREDICTO: APROBADO';
const CAMPOS = 'headRefOid,labels,commits,statusCheckRollup,state,isDraft,title,comments';

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

/** Parsea una fecha ISO; devuelve ms o NaN si falta, es ilegible o es el valor nulo de GitHub (0001-01-01). */
export function parseFecha(iso) {
  if (typeof iso !== 'string' || !iso) return NaN;
  const t = Date.parse(iso);
  if (Number.isNaN(t) || t < Date.UTC(2000, 0, 1)) return NaN;
  return t;
}

/** Evalúa estado, checks y etiquetas. Devuelve la lista de motivos de rechazo (vacía = OK). */
export function evaluarPr(data) {
  const motivos = [];
  if (data.state !== 'OPEN') motivos.push(`el PR no está abierto (estado: ${data.state})`);
  if (data.isDraft) motivos.push('el PR es un borrador');

  const checks = data.statusCheckRollup ?? [];
  const ciOk = checks.find((c) => nombreCheck(c) === CHECK_REFERENCIA);
  if (!ciOk) motivos.push(`no existe el check "${CHECK_REFERENCIA}"`);
  else if (resultadoCheck(ciOk) !== 'SUCCESS')
    motivos.push(
      `el check "${CHECK_REFERENCIA}" no está en SUCCESS (${resultadoCheck(ciOk) || 'pendiente'})`,
    );
  const fallidos = checks.filter((c) => ['FAILURE', 'ERROR'].includes(resultadoCheck(c)));
  if (fallidos.length) motivos.push(`hay checks fallidos: ${fallidos.map(nombreCheck).join(', ')}`);

  const labels = new Set((data.labels ?? []).map((l) => l.name));
  for (const l of LABELS_REQUERIDAS) if (!labels.has(l)) motivos.push(`falta la etiqueta "${l}"`);
  return motivos;
}

/**
 * Marca de tiempo de referencia: `startedAt` (o, en su defecto, `completedAt`) del CheckRun
 * `ci-ok` del commit de cabecera, registrada por GitHub. Si hay varias ejecuciones se toma la
 * más reciente. Devuelve { ms, iso } o { error }.
 */
export function referenciaCiOk(checks) {
  const runs = (checks ?? []).filter((c) => c && nombreCheck(c) === CHECK_REFERENCIA);
  if (!runs.length)
    return { error: `no existe el check "${CHECK_REFERENCIA}" para tomar la referencia temporal` };
  let mejor = null;
  for (const c of runs) {
    for (const iso of [c.startedAt, c.completedAt]) {
      const ms = parseFecha(iso);
      if (!Number.isNaN(ms)) {
        if (!mejor || ms > mejor.ms) mejor = { ms, iso };
        break; // startedAt tiene prioridad sobre completedAt dentro de la misma ejecución
      }
    }
  }
  if (!mejor)
    return {
      error: `el check "${CHECK_REFERENCIA}" no tiene startedAt/completedAt registrado por GitHub (fail-closed)`,
    };
  return mejor;
}

/** Parsea la salida de `gh api --paginate --slurp` de eventos. Lanza si es ilegible. */
export function parseEventos(stdout) {
  const data = JSON.parse(stdout);
  if (!Array.isArray(data)) throw new Error('la respuesta de eventos no es un arreglo');
  const eventos = data.flat();
  if (!eventos.every((e) => e && typeof e === 'object'))
    throw new Error('la respuesta de eventos contiene elementos inválidos');
  return eventos;
}

/** Verifica que la última aplicación de cada etiqueta requerida sea posterior a la referencia. */
export function evaluarEtiquetasVsReferencia(eventos, referenciaIso) {
  const tRef = parseFecha(referenciaIso);
  if (Number.isNaN(tRef))
    return [
      'no hay marca de tiempo de referencia válida para verificar las etiquetas (fail-closed)',
    ];
  if (!Array.isArray(eventos)) return ['no hay eventos de etiquetas legibles (fail-closed)'];
  const motivos = [];
  for (const l of LABELS_REQUERIDAS) {
    const fechas = eventos
      .filter((e) => e?.event === 'labeled' && e.label?.name === l)
      .map((e) => parseFecha(e.created_at))
      .filter((t) => !Number.isNaN(t));
    const ultima = fechas.length ? Math.max(...fechas) : NaN;
    if (!(ultima > tRef))
      motivos.push(
        `la etiqueta "${l}" no se puso después del inicio de "${CHECK_REFERENCIA}" (${referenciaIso}); revisar de nuevo`,
      );
  }
  return motivos;
}

/** ¿El texto cita el SHA (prefijo de 7+ caracteres hex)? */
export function citaSha(texto, sha) {
  if (typeof texto !== 'string' || typeof sha !== 'string' || sha.length < 7) return false;
  const full = sha.toLowerCase();
  const tokens = texto.match(/\b[0-9a-f]{7,40}\b/gi) ?? [];
  return tokens.some((t) => full.startsWith(t.toLowerCase()));
}

export const ASOCIACIONES_CONFIABLES = ['OWNER', 'MEMBER', 'COLLABORATOR'];

/**
 * Exige que el ÚLTIMO comentario de veredicto ("VEREDICTO: ...") de un autor con permisos
 * (authorAssociation OWNER/MEMBER/COLLABORATOR) sea "VEREDICTO: APROBADO" y cite el SHA de cabecera.
 * Un "CAMBIOS SOLICITADOS" posterior invalida un "APROBADO" anterior.
 */
export function evaluarVeredicto(comentarios, sha) {
  if (typeof sha !== 'string' || !/^[0-9a-f]{7,40}$/i.test(sha))
    return ['no se conoce el SHA de cabecera para verificar el veredicto (fail-closed)'];
  const veredictos = (comentarios ?? []).filter(
    (c) =>
      typeof c?.body === 'string' &&
      c.body.includes('VEREDICTO:') &&
      ASOCIACIONES_CONFIABLES.includes(String(c.authorAssociation ?? '').toUpperCase()),
  );
  const ultimo = veredictos[veredictos.length - 1];
  const ok = ultimo && ultimo.body.includes(MARCA_VEREDICTO) && citaSha(ultimo.body, sha);
  return ok
    ? []
    : [
        `el último veredicto de un autor con permisos no es "${MARCA_VEREDICTO}" citando el SHA de cabecera (${sha.slice(0, 7)})`,
      ];
}

/** Integra frescura y veredicto. `eventosRes` = { status, stdout, stderr } de gh api. */
export function evaluarFrescura({ checks, eventosRes, comentarios, sha }) {
  const motivos = [];
  const ref = referenciaCiOk(checks);
  if (ref.error) motivos.push(ref.error);
  if (!eventosRes || eventosRes.status !== 0) {
    const detalle = String(eventosRes?.stderr ?? '').trim() || 'sin respuesta';
    motivos.push(`no se pudieron leer los eventos de etiquetas (${detalle}); fail-closed`);
  } else if (!ref.error) {
    try {
      motivos.push(...evaluarEtiquetasVsReferencia(parseEventos(eventosRes.stdout), ref.iso));
    } catch (err) {
      motivos.push(`respuesta de eventos ilegible (${err.message}); fail-closed`);
    }
  }
  motivos.push(...evaluarVeredicto(comentarios, sha));
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

  let data;
  try {
    data = verPr(pr);
  } catch (err) {
    return (rechazar(pr, [err.message]), 1);
  }
  const previos = [];
  if (data.state !== 'OPEN') previos.push(`el PR no está abierto (estado: ${data.state})`);
  if (data.isDraft) previos.push('el PR es un borrador');
  if (previos.length) return (rechazar(pr, previos), 1);

  console.log(`PR #${pr}: ${data.title}\nEsperando checks…`);
  run('gh', ['pr', 'checks', pr, '--watch', '--interval', '20']); // el código se ignora a propósito

  try {
    data = verPr(pr);
  } catch (err) {
    return (rechazar(pr, [err.message]), 1);
  }
  const motivos = evaluarPr(data);

  const commits = data.commits ?? [];
  const ultimo = commits[commits.length - 1];
  const sha = data.headRefOid;
  if (ultimo?.oid && ultimo.oid !== sha)
    motivos.push(`el último commit (${ultimo.oid}) no coincide con headRefOid (${sha})`);

  const eventosRes = gh([
    'api',
    `repos/{owner}/{repo}/issues/${pr}/events?per_page=100`,
    '--paginate',
    '--slurp',
  ]);
  motivos.push(
    ...evaluarFrescura({
      checks: data.statusCheckRollup ?? [],
      eventosRes,
      comentarios: data.comments ?? [],
      sha,
    }),
  );

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
