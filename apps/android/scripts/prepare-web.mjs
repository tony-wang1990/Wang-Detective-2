import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const appDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const rootDir = path.resolve(appDir, '..', '..');
const result = spawnSync(
  process.execPath,
  [path.join(rootDir, 'scripts', 'build-client-web.mjs'), 'android', 'apps/android/www'],
  { cwd: rootDir, stdio: 'inherit' }
);

process.exit(result.status ?? 1);
