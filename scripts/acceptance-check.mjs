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

function findBash() {
  const candidates = ['bash'];
  if (process.platform === 'win32') {
    candidates.push(
      'C:\\Program Files\\Git\\bin\\bash.exe',
      'C:\\Program Files\\Git\\usr\\bin\\bash.exe'
    );
  }

  for (const candidate of candidates) {
    const result = spawnSync(candidate, ['--version'], {
      cwd: root,
      encoding: 'utf8',
      stdio: 'pipe'
    });
    if (!result.error && result.status === 0) {
      return candidate;
    }
  }

  return null;
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

check('dashboard navigation points to real routes', () => {
  const router = read('frontend/src/router/index.ts');
  const layout = read('frontend/src/layout/DashboardLayout.vue');
  const childRoutes = [...router.matchAll(/\{\s*path:\s*'([^']+)'/g)]
    .map((match) => match[1])
    .filter((route) => route && !route.includes(':') && !route.startsWith('/'))
    .map((route) => `/dashboard/${route}`);
  const knownRoutes = new Set(['/login', '/dashboard', ...childRoutes]);
  const navPaths = [...layout.matchAll(/path:\s*'([^']+)'/g)].map((match) => match[1]);
  const missing = navPaths.filter((route) => !knownRoutes.has(route));
  assert(!missing.length, `dashboard nav paths missing from router: ${missing.join(', ')}`);
});

check('shell scripts use LF line endings', () => {
  const scripts = walk('scripts', (full) => full.endsWith('.sh'));
  const cr = scripts.filter((script) => fs.readFileSync(script, 'utf8').includes('\r')).map(rel);
  assert(!cr.length, `CR bytes found in shell scripts: ${cr.join(', ')}`);
});

check('shell scripts parse with bash when available', () => {
  const bashPath = findBash();
  if (!bashPath) {
    console.log('SKIP bash is not available in this environment');
    return;
  }

  const scripts = walk('scripts', (full) => full.endsWith('.sh'));
  const bad = [];
  for (const script of scripts) {
    const result = spawnSync(bashPath, ['-n', script], {
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
    ...walk('.github', (full) => full.endsWith('.yml') || full.endsWith('.yaml')),
    ...walk('docs', (full) => full.endsWith('.md')),
    ...walk('scripts', (full) => full.endsWith('.sh') || full.endsWith('.mjs')),
    ...walk('frontend/src', (full) => full.endsWith('.vue') || full.endsWith('.ts')),
    ...walk('src/main/java/com/tony/kingdetective/telegram', (full) => full.endsWith('.java'))
  ];
  const bad = targets.filter((target) => fs.readFileSync(target, 'utf8').includes('\uFFFD')).map(rel);
  assert(!bad.length, `replacement character found in: ${bad.join(', ')}`);
});

check('key text files do not contain common mojibake markers', () => {
  const targets = [
    'README.md',
    ...walk('.github', (full) => full.endsWith('.yml') || full.endsWith('.yaml')),
    ...walk('docs', (full) => full.endsWith('.md')),
    ...walk('scripts', (full) => full.endsWith('.sh') || full.endsWith('.mjs')),
    ...walk('frontend/src', (full) => full.endsWith('.vue') || full.endsWith('.ts')),
    ...walk('src/main/java/com/tony/kingdetective/telegram', (full) => full.endsWith('.java'))
  ];
  const mojibake = /[\u9983\u9477\u9359\u93c7\u7039\u95bf\u6fe1\u65c2\u7ee0\u9410\u6d30\u52eb\u579a\u6d5c]/;
  const bad = targets.filter((target) => mojibake.test(fs.readFileSync(target, 'utf8'))).map(rel);
  assert(!bad.length, `common mojibake marker found in: ${bad.join(', ')}`);
});

check('user-visible failure copy stays professional', () => {
  const targets = [
    ...walk('src/main/java', (full) => full.endsWith('.java')),
    ...walk('frontend/src', (full) => full.endsWith('.vue') || full.endsWith('.ts'))
  ];
  const blocked = ['账号已封禁\\uD83D\\uDC7B', '\\uD83D\\uDC7B'];
  const bad = targets
    .filter((target) => blocked.some((marker) => fs.readFileSync(target, 'utf8').includes(marker)))
    .map(rel);
  assert(!bad.length, `unprofessional failure markers found in: ${bad.join(', ')}`);
});

check('README local links and images exist', () => {
  const readme = read('README.md');
  const links = [...readme.matchAll(/!?\[[^\]]*]\(([^)]+)\)/g)]
    .map((match) => match[1].trim().split('#')[0])
    .filter((target) => target && !/^[a-z][a-z0-9+.-]*:/i.test(target) && !target.startsWith('#'));
  const missing = links.filter((target) => !fs.existsSync(file(decodeURIComponent(target))));
  assert(!missing.length, `README local links/images missing: ${missing.join(', ')}`);
});

check('frontend does not use native browser dialogs', () => {
  const targets = walk('frontend/src', (full) => full.endsWith('.vue') || full.endsWith('.ts'));
  const nativeDialog = /window\.(alert|confirm|prompt)|\b(alert|confirm|prompt)\(/;
  const bad = targets.filter((target) => nativeDialog.test(fs.readFileSync(target, 'utf8'))).map(rel);
  assert(!bad.length, `native browser dialog found in: ${bad.join(', ')}`);
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

check('install and server smoke include remote smoke helpers', () => {
  const install = read('scripts/install.sh');
  const serverSmoke = read('scripts/server-smoke-test.sh');
  const helpers = ['remote-smoke-test.sh', 'remote-smoke-test.mjs'];
  for (const helper of helpers) {
    assert(install.includes(helper), `install.sh must sync ${helper}`);
  }
  assert(serverSmoke.includes('EXPECTED_NODE_HELPERS'), 'server-smoke-test.sh must declare Node helper checks');
  assert(serverSmoke.includes('remote-smoke-test.mjs'), 'server-smoke-test.sh must check remote-smoke-test.mjs presence');
});

check('Docker and install paths force UTF-8 text encoding', () => {
  const dockerfile = read('Dockerfile');
  const compose = read('docker-compose.yml');
  const install = read('scripts/install.sh');
  const logWs = read('src/main/java/com/tony/kingdetective/config/ws/LogWebSocketHandler.java');
  const tgService = read('src/main/java/com/tony/kingdetective/service/impl/TgMessageServiceImpl.java');
  const serviceLogs = read('src/main/java/com/tony/kingdetective/controller/ServiceLogController.java');

  for (const flag of ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8']) {
    assert(dockerfile.includes(flag), `Dockerfile must include ${flag}`);
    assert(compose.includes(flag), `docker-compose.yml must include ${flag}`);
    assert(install.includes(flag), `install.sh must include ${flag}`);
  }
  assert(dockerfile.includes('MAVEN_OPTS') && dockerfile.includes('-Dproject.build.sourceEncoding=UTF-8'), 'Dockerfile must force UTF-8 during Maven build');
  assert(!logWs.includes('Charset.defaultCharset()'), 'log WebSocket must not tail logs with the platform default charset');
  assert(logWs.includes('StandardCharsets.UTF_8'), 'log WebSocket must tail logs as UTF-8');
  assert(logWs.includes('TextEncodingUtils.repairMojibake'), 'log WebSocket must repair legacy mojibake before display');
  assert(tgService.includes('TextEncodingUtils.repairMojibake'), 'Telegram messages must repair legacy mojibake before send');
  assert(tgService.includes('application/json; charset=UTF-8'), 'Telegram JSON posts must declare UTF-8');
  assert(serviceLogs.includes('TextEncodingUtils.repairMojibake'), 'service log API must repair legacy mojibake before display');
});

check('low-memory VPS health checks do not report false downtime', () => {
  const healthController = read('src/main/java/com/tony/kingdetective/controller/HealthCheckController.java');
  const dockerfile = read('Dockerfile');
  const compose = read('docker-compose.yml');
  const install = read('scripts/install.sh');

  assert(healthController.includes('String status = databaseOk ? "UP" : "DOWN"'), 'memory warnings must not make an available application DOWN');
  assert(healthController.includes('.memoryStatus(memoryOk)'), 'memory warning detail must remain visible');
  assert(healthController.includes('@GetMapping("/health/liveness")'), 'health controller must expose a lightweight liveness endpoint');
  assert(dockerfile.includes('--start-period=15m'), 'Dockerfile health check must allow slow 1C/1G startup');
  assert(dockerfile.includes('/actuator/health/liveness'), 'Dockerfile must use the lightweight liveness endpoint');
  assert(compose.includes('start_period: 15m'), 'compose health check must allow slow 1C/1G startup');
  assert(compose.includes('/actuator/health/liveness'), 'compose must use the lightweight liveness endpoint');
  assert(install.includes('append_env_word "JAVA_TOOL_OPTIONS" "-XX:TieredStopAtLevel=1"'), 'install.sh must keep the low-CPU startup compilation profile');
  assert(install.includes('HEALTH_URL="http://127.0.0.1:9527/actuator/health/liveness"'), 'install.sh must wait on the lightweight liveness endpoint');
  assert(install.includes('启动超过 120 秒，输出主机资源诊断'), 'install.sh must diagnose host pressure during abnormally slow startup');
});

check('optional Telegram and AI modules do not block core web startup', () => {
  const lazyConfig = read('src/main/java/com/tony/kingdetective/config/OptionalModuleLazyConfiguration.java');
  const ociTask = read('src/main/java/com/tony/kingdetective/task/OciTask.java');
  const pom = read('pom.xml');

  assert(lazyConfig.includes('com.tony.kingdetective.telegram.handler.'), 'Telegram callback handlers must be lazy');
  assert(lazyConfig.includes('com.tony.kingdetective.controller.AiChatController'), 'AI web module must be lazy');
  assert(ociTask.includes('scheduleTgBotStartup()'), 'Telegram startup must be scheduled after core startup');
  assert(ociTask.includes('callbackHandlerFactoryProvider.getObject()'), 'Telegram handlers must warm before the bot accepts callbacks');
  assert(pom.includes('<artifactId>spring-ai-openai</artifactId>'), 'AI client library must remain available');
  assert(!pom.includes('<artifactId>spring-ai-openai-spring-boot-starter</artifactId>'), 'unused AI auto-configuration starter must be removed');
});

check('remote smoke scripts cover required routes and endpoints', () => {
  const shellSmoke = read('scripts/remote-smoke-test.sh');
  const nodeSmoke = read('scripts/remote-smoke-test.mjs');
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
  const requiredChecks = [
    'legacy-map-redirect',
    'legacy-features-redirect',
    'legacy-terminal-redirect',
    'diagnostics',
    'version-info',
    'glance',
    'sys-config',
    'oci-user-page',
    'task-page',
    'audit-recent',
    'audit-search',
    'audit-export',
    'logs-recent',
    'ops-ssh-hosts',
    'ops-ssh-sessions',
    'ops-templates',
    'backup-local',
    'backup-schedule-plan',
    'rescue-overview',
    'rescue-light-script',
    'rescue-netboot-script',
    'oci-risk',
    'vcn-page',
    'security-rules-ingress',
    'security-rules-egress'
  ];

  for (const route of requiredRoutes) {
    assert(shellSmoke.includes(route), `shell remote smoke must cover route ${route}`);
    assert(nodeSmoke.includes(route), `node remote smoke must cover route ${route}`);
  }
  for (const checkName of requiredChecks) {
    assert(shellSmoke.includes(checkName), `shell remote smoke must cover ${checkName}`);
    assert(nodeSmoke.includes(checkName), `node remote smoke must cover ${checkName}`);
  }
});

check('telegram callback buttons map to handlers', () => {
  const result = spawnSync(process.execPath, ['scripts/verify-telegram-callbacks.mjs'], {
    cwd: root,
    encoding: 'utf8',
    stdio: 'pipe'
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  assert(result.status === 0, `verify-telegram-callbacks exited ${result.status}`);
});

check('telegram callback text uses centralized MarkdownV2 formatting', () => {
  const baseHandler = read('src/main/java/com/tony/kingdetective/telegram/handler/AbstractCallbackHandler.java');
  const opsCenter = read('src/main/java/com/tony/kingdetective/telegram/handler/impl/OpsCenterHandler.java');
  const tgBot = read('src/main/java/com/tony/kingdetective/telegram/TgBot.java');
  assert(baseHandler.includes('MarkdownFormatter.formatMarkdown(text)'), 'AbstractCallbackHandler must format callback text centrally');
  assert(!opsCenter.includes('return text.replace("\\\\", "\\\\\\\\")'), 'OpsCenterSupport should not keep a partial Markdown escape implementation');
  assert(tgBot.includes('executeCallbackResponse(response, callbackData)'), 'TgBot must execute callback responses through the safe Markdown fallback');
  assert(tgBot.includes('retryCallbackResponseAsPlainText'), 'TgBot must retry callback responses as plain text when Telegram rejects MarkdownV2');
});

check('create task Telegram noise is suppressed until success', () => {
  const ociService = read('src/main/java/com/tony/kingdetective/service/impl/OciServiceImpl.java');
  assert(ociService.includes('logCreateTaskNotificationSuppressed(beginCreateMsg)'), 'create task start message should be logged instead of pushed to Telegram');
  assert(ociService.includes('sysService.sendMessage(ipMsg.toString())'), 'create task success IP notification must still be pushed to Telegram');
  assert(!ociService.includes('sysService.sendMessage(String.format("【开机任务】'), 'create task progress/failure loop messages should not push to Telegram');
});

check('shell remote smoke uses safe temp file names', () => {
  const script = read('scripts/remote-smoke-test.sh');
  assert(script.includes('safe_name='), 'scripts/remote-smoke-test.sh must sanitize check names before using them as temp filenames');
  assert(script.includes('${safe_name}.json') && script.includes('${safe_name}.err'), 'remote smoke temp files must use sanitized names');
});

check('maintenance scripts expose recovery guardrails', () => {
  const backup = read('scripts/backup.sh');
  const restore = read('scripts/restore.sh');
  const maintenance = read('scripts/maintenance.sh');
  const serverSmoke = read('scripts/server-smoke-test.sh');
  const update = read('scripts/update.sh');
  const verifyRelease = read('scripts/verify-release.sh');
  const rollback = read('scripts/rollback.sh');
  const supportBundle = read('scripts/support-bundle.sh');

  assert(!/tar -tzf "\$BACKUP_FILE"\s*\|\s*grep -q/.test(backup), 'backup.sh should not use tar | grep -q with pipefail');
  assert(!/tar -tzf "\$BACKUP_FILE"\s*\|\s*grep -q/.test(restore), 'restore.sh should not use tar | grep -q with pipefail');
  assert(!serverSmoke.includes("grep -q $'\\r'"), 'server-smoke-test.sh should use byte-level CR checks instead of grep CR checks');
  assert(serverSmoke.includes('HEALTH_SHORT_REVISION'), 'server-smoke-test.sh must compare health version with image revision');
  assert(!verifyRelease.includes("grep -Il $'\\r'"), 'verify-release.sh should use byte-level CR checks instead of grep CR checks');
  assert(restore.includes('RESTORE_VERIFY_ONLY'), 'restore.sh must support RESTORE_VERIFY_ONLY');
  assert(maintenance.includes('verify-backup'), 'maintenance.sh must expose verify-backup');
  assert(update.includes('runtime/last_successful_update'), 'update.sh must record successful update metadata');
  assert(rollback.includes('runtime/last_image_before_rollback'), 'rollback.sh must record pre-rollback metadata');
  assert(rollback.includes('RUN_SMOKE_AFTER_ROLLBACK'), 'rollback.sh must support optional post-rollback smoke test');
  assert(supportBundle.includes('redact_stream'), 'support-bundle.sh must redact sensitive output');
  assert(supportBundle.includes('ADMIN_PASSWORD=') && supportBundle.includes('TELEGRAM[^=]*TOKEN='), 'support-bundle.sh must redact common credential keys');
  assert(supportBundle.includes('TELEGRAM[^=]*CHAT_ID=') && supportBundle.includes('TG_CHAT_ID='), 'support-bundle.sh must redact Telegram chat identifiers');
  assert(supportBundle.includes('docker logs --tail') && supportBundle.includes('redact_stream'), 'support-bundle.sh must redact collected logs');
  assert(supportBundle.includes('tar -tzf "$BUNDLE_FILE"'), 'support-bundle.sh must verify generated tar bundle');
  assert(supportBundle.includes('chmod 600 "$BUNDLE_FILE"'), 'support-bundle.sh must restrict support bundle permissions');
});

if (failures.length) {
  console.error('\nAcceptance check failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('\nAcceptance check passed.');
