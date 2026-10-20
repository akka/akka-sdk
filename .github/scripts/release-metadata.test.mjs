// Test invocation and catalog contract: release-metadata/README.md
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { compareVersions, releaseFromEnvironment, updateCatalog, validateCatalog } from './release-metadata.mjs';

const empty = () => ({ schemaVersion: 1, generatedAt: null, sdk: null, runtime: null, cli: null });
const now = new Date('2026-10-20T12:00:00Z');
const sdk = {
  version: '3.7.0', releasedAt: '2026-10-16T12:00:46Z',
  releaseNotesUrl: 'https://doc.akka.io/reference/release-notes.html#sdk-3-7-0',
  requiredRuntime: '1.7.2', protocolVersion: '1.1',
};
const environment = {
  COMPONENT: 'sdk', VERSION: sdk.version, RELEASED_AT: sdk.releasedAt,
  RELEASE_NOTES_URL: sdk.releaseNotesUrl, REQUIRED_RUNTIME: sdk.requiredRuntime,
  PROTOCOL_VERSION: sdk.protocolVersion, PRERELEASE: 'false',
};
const add = (catalog, release = sdk) => updateCatalog(catalog, 'sdk', release, now);

test('compares numeric versions including four-part CLI releases', () => {
  for (const [a, b] of [['3.7.0', '3.6.99'], ['3.0.49.1', '3.0.49'], ['3.0.100', '3.0.99']]) {
    assert.ok(compareVersions(a, b) > 0);
    assert.ok(compareVersions(b, a) < 0);
    assert.equal(compareVersions(a, a), 0);
  }
});

test('replaces one component, preserving other producers and ignoring downgrades and duplicates', () => {
  const original = { ...empty(), platform: { version: '2026.42.1' } };
  const catalog = add(original);
  assert.equal(original.sdk, null);
  assert.deepEqual(catalog.sdk, sdk);
  assert.deepEqual(catalog.platform, original.platform);
  assert.equal(catalog.generatedAt, '2026-10-20T12:00:00Z');
  assert.equal(add(catalog), catalog);
  assert.equal(add(catalog, { ...sdk, version: '3.6.99' }), catalog);
  const runtime = updateCatalog(catalog, 'runtime', {
    version: '1.7.2', releasedAt: sdk.releasedAt, releaseNotesUrl: sdk.releaseNotesUrl,
    supportedProtocol: { minimum: '1.0', maximum: '1.2' },
  }, now);
  assert.deepEqual(runtime.sdk, sdk);
  const corrected = add(runtime, { ...sdk, releaseNotesUrl: `${sdk.releaseNotesUrl}-corrected` });
  assert.ok(corrected.sdk.releaseNotesUrl.endsWith('-corrected'));
  assert.deepEqual(corrected.runtime, runtime.runtime);
  const cli = updateCatalog(corrected, 'cli', {
    version: '3.0.49.1', releasedAt: sdk.releasedAt, releaseNotesUrl: sdk.releaseNotesUrl,
  }, now);
  assert.deepEqual(cli.sdk, corrected.sdk);
  assert.equal(cli.cli.version, '3.0.49.1');
});

test('ignores prerelease flags, qualifiers, and snapshots', () => {
  for (const change of [{ PRERELEASE: 'true' }, { VERSION: '3.8.0-M1' }, { VERSION: '3.8.0-RC2' }, { VERSION: '3.8.0-SNAPSHOT' }]) {
    assert.equal(releaseFromEnvironment({ ...environment, ...change }), null);
  }
});

test('validates dates, public URLs and component-specific compatibility fields', () => {
  assert.deepEqual(releaseFromEnvironment(environment), { component: 'sdk', release: sdk });
  for (const change of [
    { VERSION: 'v3.7.0' }, { PRERELEASE: 'yes' },
    { RELEASED_AT: '2026-02-30T12:00:00Z' }, { RELEASED_AT: '2026-10-16' },
    { RELEASE_NOTES_URL: 'https://github.com/private/release' },
    { REQUIRED_RUNTIME: '1.7.2-SNAPSHOT' }, { PROTOCOL_VERSION: '1' },
    { MINIMUM_PROTOCOL: '1.0' }, { COMPONENT: 'cli' }, { COMPONENT: 'unknown' },
  ]) assert.throws(() => releaseFromEnvironment({ ...environment, ...change }), JSON.stringify(change));
  assert.throws(() => updateCatalog(empty(), 'runtime', {
    version: '1.7.2', releasedAt: sdk.releasedAt, releaseNotesUrl: sdk.releaseNotesUrl,
    supportedProtocol: { minimum: '1.2', maximum: '1.0' },
  }));
});

test('rejects unsupported schemas, missing components, and the old array format', () => {
  validateCatalog(empty());
  assert.throws(() => validateCatalog({ ...empty(), schemaVersion: 2 }), /schema/);
  assert.throws(() => validateCatalog({ ...empty(), cli: undefined }), /Missing release/);
  assert.throws(() => validateCatalog({ ...empty(), sdk: { latestStable: sdk.version, releases: [sdk] } }));
  assert.throws(() => add(empty(), { ...sdk, channel: 'stable' }), /one release/);
});

test('command updates and validates files and preserves them on ignored or invalid input', () => {
  const temporary = mkdtempSync(join(tmpdir(), 'metadata-test-'));
  const path = join(temporary, 'versions.json');
  const script = fileURLToPath(new URL('./update-release-metadata.mjs', import.meta.url));
  const env = { ...process.env, MINIMUM_PROTOCOL: '', MAXIMUM_PROTOCOL: '', ...environment };
  const run = (args = [path], extra = {}) => spawnSync(process.execPath, [script, ...args], { env: { ...env, ...extra }, encoding: 'utf8' });
  try {
    assert.notEqual(run().status, 0); // A missing checked-in catalog is an error.
    writeFileSync(path, JSON.stringify(empty()));
    const first = run();
    assert.equal(first.status, 0, first.stderr);
    assert.deepEqual(JSON.parse(readFileSync(path)).sdk, sdk);
    assert.equal(run(['--validate', path]).status, 0);
    const before = readFileSync(path, 'utf8');
    for (const change of [{}, { VERSION: '3.6.99' }, { VERSION: '3.8.0-RC1' }, { PRERELEASE: 'true' }]) {
      assert.equal(run([path], change).status, 0);
      assert.equal(readFileSync(path, 'utf8'), before);
    }
    assert.notEqual(run([path], { RELEASED_AT: 'invalid' }).status, 0);
    assert.equal(readFileSync(path, 'utf8'), before);
    writeFileSync(path, '{invalid');
    assert.notEqual(run().status, 0);
    assert.equal(readFileSync(path, 'utf8'), '{invalid');
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
});
