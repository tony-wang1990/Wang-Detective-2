#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.cwd();
const failures = [];

function rel(file) {
  return path.relative(root, file).replaceAll(path.sep, '/');
}

function file(pathname) {
  return path.join(root, pathname);
}

function read(pathname) {
  return fs.readFileSync(file(pathname), 'utf8');
}

function walk(dir, predicate = () => true) {
  const fullDir = file(dir);
  if (!fs.existsSync(fullDir)) return [];
  const output = [];
  for (const entry of fs.readdirSync(fullDir, { withFileTypes: true })) {
    const full = path.join(fullDir, entry.name);
    if (entry.isDirectory()) {
      output.push(...walk(rel(full), predicate));
    } else if (predicate(full)) {
      output.push(full);
    }
  }
  return output;
}

function check(name, fn) {
  try {
    fn();
    console.log(`PASS ${name}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${name}: ${message}`);
    console.error(`FAIL ${name} - ${message}`);
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

check('acceptance matrix exists and covers current routes', () => {
  const matrixPath = 'docs/ACCEPTANCE_MATRIX.md';
  assert(fs.existsSync(file(matrixPath)), `${matrixPath} is missing`);
  const matrix = read(matrixPath);
  const requiredRoutes = [
    '/login',
    '/dashboard/home',
    '/dashboard/user',
    '/dashboard/createTask',
    '/dashboard/risk',
    '/dashboard/backups',
    '/dashboard/rescue',
    '/dashboard/features',
    '/dashboard/ops-terminal',
    '/dashboard/ai-chat',
    '/dashboard/ociLog',
    '/dashboard/ops-audit',
    '/dashboard/sysCfg'
  ];
  const missing = requiredRoutes.filter((route) => !matrix.includes(route));
  assert(!missing.length, `routes missing from acceptance matrix: ${missing.join(', ')}`);
});

check('frontend route components exist', () => {
  const router = read('frontend/src/router/index.ts');
  const imports = [...router.matchAll(/const\s+\w+\s*=\s*\(\)\s*=>\s*import\('([^']+)'\)/g)].map((match) => match[1]);
  const missing = imports
    .map((importPath) => path.join(root, 'frontend', 'src', importPath.replace(/^\.\.\//, '')))
    .filter((target) => !fs.existsSync(`${target}.vue`) && !fs.existsSync(target));
  assert(!missing.length, `missing route components: ${missing.map(rel).join(', ')}`);
});

check('shell scripts use LF line endings', () => {
  const scripts = walk('scripts', (full) => full.endsWith('.sh'));
  const crlf = scripts.filter((script) => fs.readFileSync(script, 'utf8').includes('\r\n')).map(rel);
  assert(!crlf.length, `CRLF found in shell scripts: ${crlf.join(', ')}`);
});

check('shell scripts parse with bash when available', () => {
  const bashVersion = spawnSync('bash', ['--version'], {
    cwd: root,
    encoding: 'utf8',
    stdio: 'pipe'
  });
  if (bashVersion.error) {
    console.log('SKIP bash is not available in this environment');
    return;
  }

  const scripts = walk('scripts', (full) => full.endsWith('.sh'));
  const bad = [];
  for (const script of scripts) {
    const result = spawnSync('bash', ['-n', script], {
      cwd: root,
      encoding: 'utf8',
      stdio: 'pipe'
    });
    if (result.status !== 0) {
      bad.push(`${rel(script)}: ${(result.stderr || result.stdout || '').trim()}`);
    }
  }
  assert(!bad.length, bad.join('\n'));
});

check('key text files do not contain replacement characters', () => {
  const targets = [
    'README.md',
    ...walk('docs', (full) => full.endsWith('.md')),
    ...walk('frontend/src', (full) => full.endsWith('.vue') || full.endsWith('.ts')),
    ...walk('src/main/java/com/tony/kingdetective/telegram', (full) => full.endsWith('.java'))
  ];
  const bad = targets.filter((target) => fs.readFileSync(target, 'utf8').includes('\uFFFD')).map(rel);
  assert(!bad.length, `replacement character found in: ${bad.join(', ')}`);
});

check('production dist index references existing assets', () => {
  const indexPath = 'src/main/resources/dist/index.html';
  assert(fs.existsSync(file(indexPath)), `${indexPath} is missing`);
  const index = read(indexPath);
  const refs = [...index.matchAll(/(?:src|href)="([^"]+)"/g)]
    .map((match) => match[1])
    .filter((ref) => ref.startsWith('/assets/'))
    .map((ref) => ref.replace(/^\//, 'src/main/resources/dist/'));
  const missing = refs.filter((ref) => !fs.existsSync(file(ref)));
  assert(!missing.length, `dist assets missing: ${missing.join(', ')}`);
});

check('frontend api calls map to backend controllers', () => {
  const result = spawnSync(process.execPath, ['scripts/verify-ui-api-mapping.mjs'], {
    cwd: root,
    encoding: 'utf8',
    stdio: 'pipe'
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  assert(result.status === 0, `verify-ui-api-mapping exited ${result.status}`);
});

check('remote smoke test script parses', () => {
  const result = spawnSync(process.execPath, ['--check', 'scripts/remote-smoke-test.mjs'], {
    cwd: root,
    encoding: 'utf8',
    stdio: 'pipe'
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  assert(result.status === 0, `remote-smoke-test syntax check exited ${result.status}`);
});

check('shell remote smoke uses safe temp file names', () => {
  const script = read('scripts/remote-smoke-test.sh');
  assert(script.includes('safe_name='), 'scripts/remote-smoke-test.sh must sanitize check names before using them as temp filenames');
  assert(script.includes('${safe_name}.json') && script.includes('${safe_name}.err'), 'remote smoke temp files must use sanitized names');
});

if (failures.length) {
  console.error('\nAcceptance check failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('\nAcceptance check passed.');
