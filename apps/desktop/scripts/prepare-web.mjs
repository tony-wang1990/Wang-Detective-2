import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const desktopDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootDir = resolve(desktopDir, '..', '..');

const result = spawnSync(process.execPath, [
  resolve(rootDir, 'scripts', 'build-client-web.mjs'),
  'desktop',
  'apps/desktop/web'
], {
  cwd: rootDir,
  stdio: 'inherit'
});

process.exit(result.status ?? 1);
