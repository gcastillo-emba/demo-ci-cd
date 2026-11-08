#!/usr/bin/env node
// Métricas reproducibles del POC (F6): genera las tablas de docs/metricas-demo.md a partir de
// GitHub (`gh`: PRs, corridas de Actions, comentarios de veredicto y reporte JaCoCo del último CI
// verde de main), de la bitácora de fallas y de los archivos locales de MADA (.mada/fases.jsonl,
// .mada/f0-start, .gate/last-run.json), que están en .gitignore y por eso se pasan por argumento.
// Uso:
//   node scripts/metricas.mjs [--salida docs/metricas-demo.md] [--mada <dir>] [--gate <json>]...
//                             [--sin-jacoco] [--stdout]
// Con --salida reemplaza solo el bloque entre <!-- metricas:inicio --> y <!-- metricas:fin -->;
// el resto del documento (texto redactado a mano) no se toca. Todo spawn pasa por lib/run.mjs.
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { capture } from './lib/run.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
export const INICIO = '<!-- metricas:inicio -->';
export const FIN = '<!-- metricas:fin -->';

/** PR que cierra cada fase o capacidad cuando .mada/fases.jsonl no registró el evento `fin`. */
export const PR_DE_FASE = { F1: 1, F2: 2, F3: 5, C1: 10, C2: 16, C3: 19, C4: 20 };

export function parseArgs(argv) {
  const valor = (flag) => {
    const i = argv.indexOf(flag);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  const gates = [];
  argv.forEach((a, i) => a === '--gate' && argv[i + 1] && gates.push(argv[i + 1]));
  return {
    salida: valor('--salida'),
    mada: valor('--mada') ?? '.mada',
    gates: gates.length ? gates : ['.gate/last-run.json'],
    jacoco: !argv.includes('--sin-jacoco'),
    stdout: argv.includes('--stdout') || !valor('--salida'),
  };
}

// ---------- formato ----------

export function tabla(cabecera, filas) {
  const esc = (v) => String(v ?? '—').replaceAll('|', '\\|');
  return [
    `| ${cabecera.map(esc).join(' | ')} |`,
    `| ${cabecera.map(() => '---').join(' | ')} |`,
    ...filas.map((f) => `| ${f.map(esc).join(' | ')} |`),
  ].join('\n');
}

export function duracion(ms) {
  if (ms == null || Number.isNaN(ms)) return '—';
  const min = Math.round(ms / 60000);
  if (min < 60) return `${min} min`;
  return `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, '0')} min`;
}

const hora = (iso) => (iso ? `${iso.slice(0, 10)} ${iso.slice(11, 16)}` : '—');
const pct = (cub, fal) => (cub + fal ? `${((100 * cub) / (cub + fal)).toFixed(1)} %` : '—');

// ---------- PRs ----------

export function tipoPr(pr) {
  if (pr.author?.login === 'app/dependabot' || pr.author?.is_bot) return 'dependabot';
  if (pr.state === 'CLOSED' && !pr.mergedAt) return 'reemplazado';
  const m = /^(\w+)(\(|:|!)/.exec(pr.title);
  const t = m?.[1];
  return ['feat', 'fix', 'docs', 'chore', 'ci'].includes(t) ? t : 'otro';
}

export function resumenPrs(prs) {
  const cuenta = {};
  for (const pr of prs) {
    const t = tipoPr(pr);
    const e = t === 'dependabot' ? `dependabot (${pr.state.toLowerCase()})` : t;
    cuenta[e] = (cuenta[e] ?? 0) + 1;
  }
  return cuenta;
}

// ---------- fases ----------

export function fases(eventos, f0Inicio, prs) {
  const porNumero = new Map(prs.map((p) => [p.number, p]));
  const orden = [];
  const datos = {};
  const tomar = (f) => {
    if (!datos[f]) {
      datos[f] = {};
      orden.push(f);
    }
    return datos[f];
  };
  if (f0Inicio) tomar('F0–F1').inicio = f0Inicio;
  for (const e of eventos) {
    const nombre = f0Inicio && (e.fase === 'F0' || e.fase === 'F1') ? 'F0–F1' : e.fase;
    const d = tomar(nombre);
    if (e.evento === 'inicio' && !d.inicio) d.inicio = e.ts;
    if (e.evento === 'fin') d.fin = e.ts;
  }
  return orden.map((nombre) => {
    const d = datos[nombre];
    const clave = nombre === 'F0–F1' ? 'F1' : nombre;
    const pr = porNumero.get(PR_DE_FASE[clave]);
    let fin = d.fin;
    let fuente = d.fin ? '`fin` en fases.jsonl' : '—';
    if (!fin && pr?.mergedAt) {
      fin = pr.mergedAt;
      fuente = `merge del PR #${pr.number}`;
    } else if (!fin && pr) {
      fuente = `en curso (PR #${pr.number} abierto)`;
    }
    const ms = d.inicio && fin ? Date.parse(fin) - Date.parse(d.inicio) : null;
    const ciclo = pr?.mergedAt ? Date.parse(pr.mergedAt) - Date.parse(pr.createdAt) : null;
    return {
      fase: nombre,
      inicio: d.inicio,
      fin,
      ms,
      fuente,
      pr: pr ? `#${pr.number}` : '—',
      ciclo,
    };
  });
}

// ---------- corridas de CI ----------

export function tipoFalla(workflow, job) {
  if (workflow === 'PR title') return 'semantic-title';
  if (/codeql/i.test(job)) return 'CodeQL';
  if (workflow === 'Dependabot Updates') return 'Dependabot (infraestructura)';
  if (workflow === 'Security') return 'seguridad (otros)';
  return 'CI mecánico';
}

/** `dependabot` si la corrida es de una rama de Dependabot (o del job Dependabot en main). */
export function origen(rama, workflow) {
  if (workflow === 'Dependabot Updates') return 'dependabot';
  return /^dependabot\//.test(rama ?? '') ? 'dependabot' : 'agente';
}

export function resumenCorridas(corridas) {
  const porWorkflow = {};
  for (const r of corridas) {
    const w = (porWorkflow[r.workflowName] ??= { total: 0 });
    w.total++;
    const c = r.conclusion || r.status || 'sin_conclusion';
    w[c] = (w[c] ?? 0) + 1;
  }
  return porWorkflow;
}

// ---------- veredictos ----------

export function veredictos(comentarios) {
  const porPr = {};
  for (const c of comentarios) {
    const pr = Number(c.issue_url.split('/').pop());
    const d = (porPr[pr] ??= { aprobado: 0, cambios: 0, seguridad: 0, retenida: 0 });
    const b = c.body ?? '';
    if (/^VEREDICTO:\s*APROBADO/m.test(b)) d.aprobado++;
    if (/^VEREDICTO:\s*CAMBIOS SOLICITADOS/m.test(b)) d.cambios++;
    if (/^## Revisión de seguridad/m.test(b)) {
      d.seguridad++;
      if (/No pongo la etiqueta|retenida/i.test(b)) d.retenida++;
    }
  }
  return porPr;
}

// ---------- bitácora ----------

export function filasBitacora(md) {
  return md
    .split('\n')
    .filter((l) => /^\|\s*\d+\s*\|/.test(l))
    .map((l) => {
      const c = l.split('|').map((s) => s.trim());
      return { n: Number(c[1]), fecha: c[2], fase: c[3], que: c[4], causa: c[5] };
    });
}

export function categoriaBitacora(f) {
  const t = `${f.que} ${f.causa}`;
  if (/^Gate de juicio|CAMBIOS SOLICITADOS|Revisión PR/i.test(f.que)) return 'gate de juicio';
  if (/CodeQL/.test(t)) return 'CodeQL';
  if (/semantic-title/.test(t)) return 'semantic-title';
  if (/compose|worktree/i.test(t) && /paralelo|colisi|comparten|mismo proyecto/i.test(t))
    return 'concurrencia entre worktrees';
  if (/clasificador|subagent|heredoc|sandbox|reemplazarse|force push/i.test(t))
    return 'herramienta / Claude Code';
  if (/spec|design|tarea|orquestador|escenario|supuesto/i.test(f.que)) return 'spec o instrucción';
  if (/gate\.mjs|en rojo|falló|falla/i.test(f.que)) return 'gate mecánico local';
  return 'entorno / dependencias';
}

// ---------- JaCoCo ----------

export function jacocoCapas(csv, prefijos = ['domain', 'application']) {
  const base = 'com.emba.asuntoshoras.';
  const acc = { ins: [0, 0], ram: [0, 0], lin: [0, 0] };
  for (const linea of csv.trim().split('\n').slice(1)) {
    const c = linea.split(',');
    const paquete = c[1] ?? '';
    if (!prefijos.some((p) => paquete.startsWith(base + p))) continue;
    acc.ins[0] += +c[3];
    acc.ins[1] += +c[4];
    acc.ram[0] += +c[5];
    acc.ram[1] += +c[6];
    acc.lin[0] += +c[7];
    acc.lin[1] += +c[8];
  }
  return {
    instrucciones: pct(acc.ins[1], acc.ins[0]),
    ramas: pct(acc.ram[1], acc.ram[0]),
    lineas: pct(acc.lin[1], acc.lin[0]),
  };
}

// ---------- gate local ----------

export function filaGate(ruta, j) {
  const total = j.steps.reduce((a, s) => a + s.ms, 0);
  const lento = [...j.steps].sort((a, b) => b.ms - a.ms)[0];
  const fallas = j.steps.filter((s) => s.status === 'FAIL').map((s) => s.id);
  return [
    ruta,
    hora(j.ts),
    j.args?.length ? j.args.join(' ') : '(completo sin E2E)',
    `${(total / 1000).toFixed(1)} s`,
    lento ? `${lento.id} (${(lento.ms / 1000).toFixed(1)} s)` : '—',
    fallas.length ? fallas.join(', ') : 'todo PASS',
  ];
}

// ---------- recolección (gh) ----------

function gh(args) {
  const r = capture('gh', args, { cwd: ROOT, maxBuffer: 64 * 1024 * 1024 });
  if (r.status !== 0) throw new Error(`gh ${args.join(' ')}: ${r.stderr.trim()}`);
  return JSON.parse(r.stdout || 'null');
}

function ghPaginado(ruta) {
  const r = capture('gh', ['api', ruta, '--paginate', '--slurp'], {
    cwd: ROOT,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (r.status !== 0) throw new Error(`gh api ${ruta}: ${r.stderr.trim()}`);
  return JSON.parse(r.stdout).flat();
}

function jobsFallidos(id) {
  const { jobs } = gh(['run', 'view', String(id), '--json', 'jobs']);
  return jobs.filter((j) => j.conclusion === 'failure').map((j) => j.name);
}

function coberturaCi(repo) {
  const [ultima] = gh([
    'run',
    'list',
    '-R',
    repo,
    '-w',
    'CI',
    '-b',
    'main',
    '-s',
    'success',
    '-L',
    '1',
    '--json',
    'databaseId,headSha,createdAt',
  ]);
  if (!ultima) return null;
  // mkdtemp crea un directorio propio 0700 con nombre aleatorio (no un nombre predecible en /tmp).
  const dir = mkdtempSync(join(tmpdir(), 'metricas-jacoco-'));
  try {
    const r = capture(
      'gh',
      ['run', 'download', String(ultima.databaseId), '-R', repo, '-n', 'jacoco-report', '-D', dir],
      { cwd: ROOT },
    );
    if (r.status !== 0) return null;
    const csv = readFileSync(join(dir, 'jacoco.csv'), 'utf8');
    return { ...jacocoCapas(csv), run: ultima };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

export function leerJsonl(ruta) {
  if (!existsSync(ruta)) return [];
  return readFileSync(ruta, 'utf8')
    .split('\n')
    .filter((l) => l.trim())
    .map((l) => JSON.parse(l));
}

// ---------- documento ----------

export function bloque(d) {
  const s = [];
  s.push(
    `> Generado con \`node scripts/metricas.mjs\` el ${d.generado.slice(0, 16).replace('T', ' ')} UTC sobre \`${d.repo}\`. No editar a mano entre los marcadores.`,
  );

  s.push('', '### Tiempo por fase', '');
  s.push(
    tabla(
      [
        'Fase',
        'Inicio (UTC)',
        'Fin (UTC)',
        'Duración',
        'Fuente del fin',
        'PR',
        'PR abierto → merge',
      ],
      d.fases.map((f) => [
        f.fase,
        hora(f.inicio),
        hora(f.fin),
        duracion(f.ms),
        f.fuente,
        f.pr,
        duracion(f.ciclo),
      ]),
    ),
  );
  if (!d.fases.length) s.push('', '_Sin `.mada/fases.jsonl`: pasar `--mada <dir>`._');

  s.push('', '### Pull requests', '');
  s.push(
    tabla(
      ['Tipo', 'Cantidad'],
      Object.entries(d.resumenPrs).sort(([a], [b]) => a.localeCompare(b)),
    ),
  );
  s.push('', `Total: ${d.prs.length} PRs.`, '');
  s.push(
    tabla(
      ['PR', 'Título', 'Tipo', 'Estado', 'Abierto (UTC)', 'Abierto → merge'],
      [...d.prs]
        .sort((a, b) => a.number - b.number)
        .map((p) => [
          `#${p.number}`,
          p.title,
          tipoPr(p),
          p.state.toLowerCase(),
          hora(p.createdAt),
          p.mergedAt ? duracion(Date.parse(p.mergedAt) - Date.parse(p.createdAt)) : '—',
        ]),
    ),
  );

  s.push('', '### Corridas de GitHub Actions', '');
  s.push(
    tabla(
      ['Workflow', 'Corridas', 'success', 'failure', 'cancelled', 'skipped'],
      Object.entries(d.corridas)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([w, c]) => [
          w,
          c.total,
          c.success ?? 0,
          c.failure ?? 0,
          c.cancelled ?? 0,
          c.skipped ?? 0,
        ]),
    ),
  );
  s.push(
    '',
    `Reintentos (corridas con \`attempt\` > 1, p. ej. \`gh run rerun --failed\`): **${d.reintentos}**.`,
  );

  s.push('', '### Fallas de gates mecánicos en CI', '');
  const porTipo = {};
  for (const f of d.fallas) {
    const col = origen(f.headBranch, f.workflowName);
    for (const t of f.tipos) {
      porTipo[t] ??= { agente: 0, dependabot: 0 };
      porTipo[t][col]++;
    }
  }
  s.push(
    tabla(
      ['Tipo', 'Jobs fallidos en ramas del agente', 'Jobs fallidos en ramas de Dependabot'],
      Object.entries(porTipo)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([t, c]) => [t, c.agente, c.dependabot]),
    ),
  );
  s.push(
    '',
    'Las fallas de CodeQL por **alertas** (no por jobs caídos) se publican como el check `CodeQL` del commit y no aparecen aquí; ver la bitácora.',
  );
  s.push('');
  s.push(
    tabla(
      ['Fecha (UTC)', 'Workflow', 'Rama', 'Intento', 'Jobs fallidos'],
      d.fallas.map((f) => [
        hora(f.createdAt),
        f.workflowName,
        `\`${f.headBranch}\``,
        f.attempt,
        f.jobs.join(', ') || '(sin jobs: falla de arranque)',
      ]),
    ),
  );

  s.push('', '### Gates de juicio (comentarios de veredicto)', '');
  const v = Object.entries(d.veredictos).filter(
    ([, x]) => x.aprobado + x.cambios + x.seguridad > 0,
  );
  s.push(
    tabla(
      [
        'PR',
        'revisor: CAMBIOS SOLICITADOS',
        'revisor: APROBADO',
        'seguridad: rondas',
        'seguridad: etiqueta retenida',
      ],
      v.map(([pr, x]) => [`#${pr}`, x.cambios, x.aprobado, x.seguridad, x.retenida]),
    ),
  );
  const tot = v.reduce(
    (a, [, x]) => ({ c: a.c + x.cambios, a: a.a + x.aprobado, r: a.r + x.retenida }),
    { c: 0, a: 0, r: 0 },
  );
  s.push(
    '',
    `Totales: ${tot.c} CAMBIOS SOLICITADOS, ${tot.a} APROBADO, ${tot.r} etiquetas \`seguridad:ok\` retenidas por SAST incompleto.`,
  );

  s.push('', '### Bitácora de fallas', '');
  const porFase = {};
  const porCat = {};
  for (const f of d.bitacora) {
    porFase[f.fase] = (porFase[f.fase] ?? 0) + 1;
    const c = categoriaBitacora(f);
    porCat[c] = (porCat[c] ?? 0) + 1;
  }
  s.push(tabla(['Fase', 'Filas'], Object.entries(porFase)));
  s.push('');
  s.push(
    tabla(
      ['Categoría (heurística por palabras clave)', 'Filas'],
      Object.entries(porCat).sort(([, a], [, b]) => b - a),
    ),
  );
  s.push('', `Total: ${d.bitacora.length} filas.`);

  s.push('', '### Cobertura JaCoCo (`domain` + `application`)', '');
  if (d.jacoco) {
    s.push(
      tabla(
        ['Instrucciones', 'Ramas', 'Líneas', 'Fuente'],
        [
          [
            d.jacoco.instrucciones,
            d.jacoco.ramas,
            d.jacoco.lineas,
            `artefacto \`jacoco-report\` del CI ${d.jacoco.run.databaseId} (main, ${d.jacoco.run.headSha.slice(0, 7)})`,
          ],
        ],
      ),
    );
  } else {
    s.push('_No disponible (sin `gh run download` o `--sin-jacoco`)._');
  }

  s.push('', '### Gate local (`.gate/last-run.json`)', '');
  if (d.gates.length) {
    s.push(
      tabla(['Archivo', 'Fecha (UTC)', 'Modo', 'Total', 'Paso más lento', 'Resultado'], d.gates),
    );
  } else {
    s.push('_Sin `.gate/last-run.json`: pasar `--gate <ruta>`._');
  }
  return s.join('\n');
}

export function reemplazarBloque(doc, contenido) {
  const i = doc.indexOf(INICIO);
  const j = doc.indexOf(FIN);
  if (i < 0 || j < i) throw new Error(`faltan los marcadores ${INICIO} … ${FIN}`);
  return `${doc.slice(0, i + INICIO.length)}\n\n${contenido}\n\n${doc.slice(j)}`;
}

async function formatear(texto, ruta) {
  try {
    const prettier = await import('prettier');
    const config = (await prettier.resolveConfig(ruta)) ?? {};
    return await prettier.format(texto, { ...config, filepath: ruta });
  } catch {
    return texto;
  }
}

export async function main(argv = process.argv.slice(2)) {
  const flags = parseArgs(argv);
  const { nameWithOwner: repo } = gh(['repo', 'view', '--json', 'nameWithOwner']);
  const prs = gh([
    'pr',
    'list',
    '--state',
    'all',
    '--limit',
    '500',
    '--json',
    'number,title,state,createdAt,mergedAt,author,headRefName',
  ]);
  const corridas = gh([
    'run',
    'list',
    '--limit',
    '1000',
    '--json',
    'databaseId,workflowName,event,status,conclusion,headBranch,createdAt,attempt',
  ]);
  const fallas = corridas
    .filter((r) => r.conclusion === 'failure')
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
    .map((r) => {
      const jobs = jobsFallidos(r.databaseId);
      const tipos = (jobs.length ? jobs : ['(arranque)']).map((j) => tipoFalla(r.workflowName, j));
      return { ...r, jobs, tipos };
    });
  const comentarios = ghPaginado(`repos/${repo}/issues/comments?per_page=100`);

  const mada = resolve(ROOT, flags.mada);
  const eventos = leerJsonl(join(mada, 'fases.jsonl'));
  const f0 = existsSync(join(mada, 'f0-start'))
    ? readFileSync(join(mada, 'f0-start'), 'utf8').trim()
    : undefined;

  const gates = flags.gates
    .map((g) => resolve(ROOT, g))
    .filter((g) => existsSync(g))
    .map((g) => filaGate(basename(dirname(dirname(g))), JSON.parse(readFileSync(g, 'utf8'))));

  const contenido = bloque({
    generado: new Date().toISOString(),
    repo,
    fases: fases(eventos, f0, prs),
    prs,
    resumenPrs: resumenPrs(prs),
    corridas: resumenCorridas(corridas),
    reintentos: corridas.filter((r) => r.attempt > 1).length,
    fallas,
    veredictos: veredictos(comentarios),
    bitacora: filasBitacora(readFileSync(join(ROOT, 'docs/bitacora-fallas.md'), 'utf8')),
    jacoco: flags.jacoco ? coberturaCi(repo) : null,
    gates,
  });

  if (flags.salida) {
    const ruta = resolve(ROOT, flags.salida);
    const doc = reemplazarBloque(readFileSync(ruta, 'utf8'), contenido);
    writeFileSync(ruta, await formatear(doc, ruta));
    console.log(`✓ ${flags.salida} actualizado (${prs.length} PRs, ${corridas.length} corridas).`);
  }
  if (flags.stdout) console.log(contenido);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  try {
    process.exitCode = await main();
  } catch (e) {
    console.error(`✗ ${e.message}`);
    process.exitCode = 1;
  }
}
