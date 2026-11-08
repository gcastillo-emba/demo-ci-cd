import { test } from 'node:test';
import assert from 'node:assert/strict';
import { globToRegex, matchesAny, outsideAllowed, parseArgs } from '../check-ownership.mjs';

test('** al final cubre cualquier profundidad', () => {
  const re = globToRegex('backend/**');
  assert.ok(re.test('backend/pom.xml'));
  assert.ok(re.test('backend/src/main/java/A.java'));
  assert.ok(!re.test('frontend/a.ts'));
  assert.ok(!re.test('backend2/a'));
});

test('**/ al inicio cubre cero o más directorios', () => {
  const re = globToRegex('**/test/**');
  assert.ok(re.test('test/a.mjs'));
  assert.ok(re.test('scripts/test/a.mjs'));
  assert.ok(!re.test('scripts/testing/a.mjs'));
});

test('* no cruza separadores', () => {
  const re = globToRegex('openspec/changes/*/design.md');
  assert.ok(re.test('openspec/changes/add-x/design.md'));
  assert.ok(!re.test('openspec/changes/a/b/design.md'));
  assert.ok(!re.test('openspec/changes/add-x/tasks.md'));
});

test('patrón con extensión', () => {
  const re = globToRegex('**/*.test.*');
  assert.ok(re.test('a.test.mjs'));
  assert.ok(re.test('frontend/src/x.test.ts'));
  assert.ok(!re.test('frontend/src/xtest.ts'));
});

test('los puntos y otros metacaracteres se escapan', () => {
  const re = globToRegex('docker-compose*.yml');
  assert.ok(re.test('docker-compose.e2e.yml'));
  assert.ok(!re.test('docker-composeXyml'));
});

test('normaliza barras invertidas y ./ inicial', () => {
  assert.ok(matchesAny('scripts\\gate.mjs', ['./scripts/**']));
});

test('outsideAllowed lista solo los archivos fuera de los globs', () => {
  const files = ['backend/a.java', 'frontend/b.ts', 'docs/c.md'];
  assert.deepEqual(outsideAllowed(files, ['backend/**', 'docs/**']), ['frontend/b.ts']);
});

test('parseArgs separa base, head y globs', () => {
  assert.deepEqual(parseArgs(['main', 'HEAD', '--allow', 'a/**, b/*.md']), {
    base: 'main',
    head: 'HEAD',
    globs: ['a/**', 'b/*.md'],
  });
  assert.deepEqual(parseArgs(['x', 'y', '--allow=c/**']).globs, ['c/**']);
});
