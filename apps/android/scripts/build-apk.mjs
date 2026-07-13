import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { homedir } from 'node:os';
import path from 'node:path';

const appDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const androidDir = path.join(appDir, 'android');
const mode = process.argv[2] === 'release' ? 'release' : 'debug';
const buildEnv = { ...process.env };

if (!buildEnv.ANDROID_HOME && !buildEnv.ANDROID_SDK_ROOT) {
  const sdkCandidates = [
    process.platform === 'win32' && buildEnv.LOCALAPPDATA
      ? path.join(buildEnv.LOCALAPPDATA, 'Android', 'Sdk')
      : '',
    process.platform === 'win32' ? 'C:\\Android\\Sdk' : '',
    process.platform === 'darwin'
      ? path.join(homedir(), 'Library', 'Android', 'sdk')
      : path.join(homedir(), 'Android', 'Sdk')
  ].filter(Boolean);
  const detectedSdk = sdkCandidates.find(candidate => existsSync(candidate));
  if (detectedSdk) {
    buildEnv.ANDROID_HOME = detectedSdk;
    buildEnv.ANDROID_SDK_ROOT = detectedSdk;
  }
}

function run(command, args, cwd = appDir) {
  const result = spawnSync(command, args, {
    cwd,
    stdio: 'inherit',
    env: buildEnv,
    shell: process.platform === 'win32' && /\.(cmd|bat)$/i.test(command)
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

run(process.execPath, [path.join(appDir, 'scripts', 'prepare-web.mjs')]);

const capCommand = process.platform === 'win32' ? 'npx.cmd' : 'npx';
if (!existsSync(androidDir)) {
  run(capCommand, ['cap', 'add', 'android']);
}
run(capCommand, ['cap', 'sync', 'android']);

const gradleCommand = process.platform === 'win32'
  ? path.join(androidDir, 'gradlew.bat')
  : path.join(androidDir, 'gradlew');
run(gradleCommand, [mode === 'release' ? 'assembleRelease' : 'assembleDebug'], androidDir);

const output = mode === 'release'
  ? 'android/app/build/outputs/apk/release/'
  : 'android/app/build/outputs/apk/debug/app-debug.apk';
console.log(`Android ${mode} build completed: ${output}`);
