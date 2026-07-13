import { createHash } from 'node:crypto';
import { createReadStream, existsSync, mkdirSync, copyFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const [kind, sourceArg] = process.argv.slice(2);
const names = {
  android: 'wang-detective-latest.apk',
  windows: 'Wang-Detective-Setup-latest.exe'
};

if (!names[kind] || !sourceArg) {
  console.error('Usage: node scripts/publish-client-package.mjs <android|windows> <package-file>');
  process.exit(2);
}

const source = path.resolve(process.cwd(), sourceArg);
if (!existsSync(source)) {
  console.error(`Package not found: ${source}`);
  process.exit(2);
}

const downloadDir = path.join(rootDir, 'deploy', 'downloads');
const target = path.join(downloadDir, names[kind]);
mkdirSync(downloadDir, { recursive: true });
copyFileSync(source, target);

const digest = createHash('sha256');
await new Promise((resolve, reject) => {
  const input = createReadStream(target);
  input.on('data', chunk => digest.update(chunk));
  input.on('end', resolve);
  input.on('error', reject);
});

const sha256 = digest.digest('hex');
writeFileSync(`${target}.sha256`, `${sha256}  ${path.basename(target)}\n`, 'utf8');
console.log(`Published: ${target}`);
console.log(`SHA256: ${sha256}`);
