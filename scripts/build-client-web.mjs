import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [kind = 'web', outDir = 'src/main/resources/dist'] = process.argv.slice(2);

if (!['web', 'desktop', 'android'].includes(kind)) {
  console.error(`Unsupported client kind: ${kind}`);
  process.exit(1);
}

const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const env = {
  ...process.env,
  VITE_WD_CLIENT_KIND: kind === 'web' ? '' : kind,
  VITE_WD_CLIENT_OUTDIR: resolve(rootDir, outDir)
};

const result = spawnSync(npm, ['--prefix', 'frontend', 'run', 'build'], {
  cwd: rootDir,
  env,
  stdio: 'inherit',
  shell: process.platform === 'win32'
});

process.exit(result.status ?? 1);
