#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const frontendDir = path.join(root, 'frontend', 'src');
const controllerDir = path.join(root, 'src', 'main', 'java', 'com', 'tony', 'kingdetective', 'controller');

function walk(dir, predicate = () => true) {
  if (!fs.existsSync(dir)) return [];
  const result = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      result.push(...walk(full, predicate));
    } else if (predicate(full)) {
      result.push(full);
    }
  }
  return result;
}

function normalize(pathname) {
  return pathname.replace(/\/+/g, '/').replace(/\/$/, '') || '/';
}

function controllerEndpoints() {
  const files = walk(controllerDir, (file) => file.endsWith('.java'));
  const endpoints = new Set();
  const classMappingRe = /@RequestMapping\s*\(\s*(?:path\s*=\s*)?"([^"]+)"/;
  const methodMappingRe = /@(GetMapping|PostMapping|PutMapping|DeleteMapping)\s*(?:\(\s*(?:path\s*=\s*)?"([^"]*)")?/g;

  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    const classMatch = text.match(classMappingRe);
    const base = normalize(classMatch?.[1] || '');
    for (const match of text.matchAll(methodMappingRe)) {
      const route = match[2] ?? '';
      endpoints.add(normalize(`${base}/${route}`));
    }
  }
  return endpoints;
}

function frontendCalls() {
  const files = walk(frontendDir, (file) => file.endsWith('.ts') || file.endsWith('.vue'));
  const calls = [];
  const callRe = /\b(apiGet|apiPost|apiPostLong|apiDownload|apiForm|opsGet|opsPost|opsPut|opsDelete|opsDownload|opsDownloadWithProgress|rawGet)\s*(?:<[^>]+>)?\s*\(\s*([`'"])([^`'"]+)/g;

  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    for (const match of text.matchAll(callRe)) {
      const helper = match[1];
      const literal = match[3];
      if (literal.startsWith('http') || literal.startsWith('ws') || literal.startsWith('/actuator')) {
        continue;
      }
      let url = literal;
      url = url.replace(/\$\{[^}]+}/g, '{param}');
      if (helper.startsWith('ops')) {
        url = `/ops${url}`;
      }
      if (!url.startsWith('/api')) {
        url = `/api${url}`;
      }
      url = normalize(url.split('?')[0]);
      calls.push({ file: path.relative(root, file), helper, url });
    }
  }
  return calls;
}

function routeMatches(call, endpoints) {
  if (endpoints.has(call.url)) return true;
  const callParts = normalize(call.url).split('/').filter(Boolean);
  return [...endpoints].some((endpoint) => {
    const endpointParts = normalize(endpoint).split('/').filter(Boolean);
    if (endpointParts.length !== callParts.length) return false;
    return endpointParts.every((part, index) => (
      part.startsWith('{') && part.endsWith('}')
    ) || (
      callParts[index].startsWith('{') && callParts[index].endsWith('}')
    ) || part === callParts[index]);
  });
}

const endpoints = controllerEndpoints();
const calls = frontendCalls();
const missing = calls.filter((call) => !routeMatches(call, endpoints));

console.log(`Controller endpoints: ${endpoints.size}`);
console.log(`Frontend API calls: ${calls.length}`);

if (missing.length) {
  console.error('Missing backend mappings for frontend calls:');
  for (const item of missing) {
    console.error(`- ${item.url} (${item.helper}) in ${item.file}`);
  }
  process.exit(1);
}

console.log('Frontend API calls all have matching controller mappings.');
