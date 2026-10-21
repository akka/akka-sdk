// Test invocation and catalog contract: release-metadata/README.md
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync, rmSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { compareVersions, releaseFromEnvironment, updateCatalog, validateCatalog } from './release-metadata.mjs';

const empty = () => ({ schemaVersion: 1, sdk: { releases: [] }, runtime: { releases: [] }, cli: { releases: [] } });
const sdk = {
  version: '3.7.0', releasedAt: '2026-10-16T12:00:46Z', notifyAt: '2026-10-26T12:00:46Z',
  releaseNotesUrl: 'https://doc.akka.io/reference/release-notes.html#sdk-3-7-0',
  requiredRuntime: '1.7.2', protocolVersion: '1.1',
};
const environment = {
  COMPONENT: 'sdk', VERSION: sdk.version, RELEASED_AT: sdk.releasedAt,
  RELEASE_NOTES_URL: sdk.releaseNotesUrl, REQUIRED_RUNTIME: sdk.requiredRuntime,
  PROTOCOL_VERSION: sdk.protocolVersion, PRERELEASE: 'false', NOTIFY_DELAY_DAYS: '',
};
const add = (catalog, release = sdk) => updateCatalog(catalog, 'sdk', release);

test('compares numeric versions including four-part CLI releases', () => {
  for (const [a, b] of [['3.7.0', '3.6.99'], ['3.0.49.1', '3.0.49'], ['3.0.100', '3.0.99']]) {
    assert.ok(compareVersions(a, b) > 0);
    assert.ok(compareVersions(b, a) < 0);
    assert.equal(compareVersions(a, a), 0);
  }
});

test('retains sorted history, preserves other components, and corrects the same version', () => {
  const original = { ...empty(), platform: { version: '2026.42.1' } };
  const catalog = add(original);
  assert.deepEqual(original.sdk.releases, []);
  assert.deepEqual(catalog.sdk.releases, [sdk]);
  assert.deepEqual(catalog.platform, original.platform);
  assert.equal(add(catalog), catalog);
  const newer = add(catalog, { ...sdk, version: '3.7.1' });
  const historical = add(newer, { ...sdk, version: '3.6.99' });
  assert.deepEqual(historical.sdk.releases.map(r => r.version), ['3.7.1', '3.7.0', '3.6.99']);
  const runtime = updateCatalog(historical, 'runtime', {
    version: '1.7.2', releasedAt: sdk.releasedAt, releaseNotesUrl: sdk.releaseNotesUrl,
    supportedProtocol: { minimum: '1.0', maximum: '1.2' },
  });
  assert.deepEqual(runtime.sdk, historical.sdk);
  const corrected = add(runtime, { ...sdk, releaseNotesUrl: `${sdk.releaseNotesUrl}-corrected` });
  assert.ok(corrected.sdk.releases[1].releaseNotesUrl.endsWith('-corrected'));
  assert.equal(corrected.sdk.releases.length, 3);
  assert.deepEqual(corrected.runtime, runtime.runtime);
  const cli = updateCatalog(corrected, 'cli', {
    version: '3.0.49.1', releasedAt: sdk.releasedAt, releaseNotesUrl: sdk.releaseNotesUrl,
  });
  assert.deepEqual(cli.sdk, corrected.sdk);
  assert.equal(cli.cli.releases[0].version, '3.0.49.1');
});

test('weekly SDK releases remain eligible after their delay and zero-day corrections take effect', () => {
  let catalog = empty();
  for (const [version, date, runtime] of [['3.7.0', '01', '1.7.1'], ['3.7.1', '08', '1.7.2'], ['3.7.2', '15', '1.7.3']]) {
    const { release } = releaseFromEnvironment({ ...environment, VERSION: version, REQUIRED_RUNTIME: runtime, RELEASED_AT: `2026-10-${date}T12:00:00Z` });
    catalog = add(catalog, release);
  }
  const eligible = date => catalog.sdk.releases.find(r => r.notifyAt <= date)?.version;
  assert.equal(eligible('2026-10-21T12:00:00Z'), '3.7.1');
  assert.equal(eligible('2026-10-25T11:59:59Z'), '3.7.1');
  assert.equal(eligible('2026-10-25T12:00:00Z'), '3.7.2');
  const { release } = releaseFromEnvironment({ ...environment, VERSION: '3.7.2', REQUIRED_RUNTIME: '1.7.3', RELEASED_AT: '2026-10-15T12:00:00Z', NOTIFY_DELAY_DAYS: '0' });
  assert.equal(release.notifyAt, release.releasedAt);
  catalog = add(catalog, release);
  assert.equal(catalog.sdk.releases.length, 3);
  assert.equal(eligible('2026-10-21T12:00:00Z'), '3.7.2');
});

test('unchanged Runtime requirements skip the delay without changing incoming metadata', () => {
  const first = add(empty());
  assert.equal(first.sdk.releases[0].notifyAt, sdk.notifyAt);
  const next = { ...sdk, version: '3.7.1' };
  const immediate = add(first, next);
  assert.equal(immediate.sdk.releases[0].notifyAt, next.releasedAt);
  assert.equal(next.notifyAt, sdk.notifyAt);
  assert.equal(add(immediate, next), immediate);
  const changed = { ...sdk, version: '3.7.2', requiredRuntime: '1.7.3' };
  const delayed = add(immediate, changed);
  assert.equal(delayed.sdk.releases[0].notifyAt, changed.notifyAt);
  // A historical release uses its predecessor rather than the newest SDK entry.
  const historical = add(delayed, { ...sdk, version: '3.7.0.1' });
  assert.equal(historical.sdk.releases[2].notifyAt, sdk.releasedAt);
});

test('only SDK entries receive notifyAt and delay inputs are validated', () => {
  assert.equal(releaseFromEnvironment(environment).release.notifyAt, sdk.notifyAt);
  for (const delay of ['-1', '1.5', 'NaN', 'Infinity', '999999999999999999999999']) {
    assert.throws(() => releaseFromEnvironment({ ...environment, NOTIFY_DELAY_DAYS: delay }), /delay/);
  }
  const cli = releaseFromEnvironment({ ...environment, COMPONENT: 'cli', REQUIRED_RUNTIME: '', PROTOCOL_VERSION: '' });
  assert.equal(cli.release.notifyAt, undefined);
  const runtime = releaseFromEnvironment({ ...environment, COMPONENT: 'runtime', REQUIRED_RUNTIME: '', PROTOCOL_VERSION: '', MINIMUM_PROTOCOL: '1.0', MAXIMUM_PROTOCOL: '1.2' });
  assert.equal(runtime.release.notifyAt, undefined);
  assert.throws(() => updateCatalog(empty(), 'cli', { ...cli.release, notifyAt: sdk.notifyAt }), /Only SDK/);
  assert.throws(() => add(empty(), { ...sdk, notifyAt: sdk.releasedAt.replace('2026', '2025') }), /precede/);
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

test('release notes can be supplied later without a blank resubmission erasing them', () => {
  for (const url of ['', undefined]) {
    const { release } = releaseFromEnvironment({ ...environment, RELEASE_NOTES_URL: url });
    assert.equal(release.releaseNotesUrl, '');
    const initial = add(empty(), release);
    validateCatalog(initial);
    const designated = add(initial, sdk);
    assert.equal(designated.sdk.releases[0].releaseNotesUrl, sdk.releaseNotesUrl);
    assert.equal(add(designated, release), designated);
  }
  const cli = releaseFromEnvironment({ ...environment, COMPONENT: 'cli', REQUIRED_RUNTIME: '', PROTOCOL_VERSION: '', RELEASE_NOTES_URL: '' });
  validateCatalog(updateCatalog(empty(), 'cli', cli.release));
  const runtime = releaseFromEnvironment({ ...environment, COMPONENT: 'runtime', REQUIRED_RUNTIME: '', PROTOCOL_VERSION: '', MINIMUM_PROTOCOL: '1.0', MAXIMUM_PROTOCOL: '1.2', RELEASE_NOTES_URL: '' });
  validateCatalog(updateCatalog(empty(), 'runtime', runtime.release));
  assert.throws(() => add(empty(), { ...sdk, releaseNotesUrl: null }), /string/);
  assert.throws(() => add(empty(), { ...sdk, releaseNotesUrl: ' ' }), /https/);
});

test('validates release lists, rejects duplicate versions and requires SDK notifyAt', () => {
  validateCatalog(empty());
  assert.throws(() => validateCatalog({ ...empty(), schemaVersion: 2 }), /schema/);
  assert.throws(() => validateCatalog({ ...empty(), cli: undefined }), /array/);
  assert.throws(() => validateCatalog({ ...empty(), sdk }), /array/);
  assert.throws(() => validateCatalog({ ...empty(), sdk: { releases: [sdk, sdk] } }), /Duplicate/);
  assert.throws(() => validateCatalog({ ...empty(), sdk: { releases: [sdk, { ...sdk, version: '3.7.1' }] } }), /newest first/);
  const { notifyAt, ...withoutNotification } = sdk;
  assert.throws(() => add(empty(), withoutNotification), /timestamp/);
  assert.throws(() => add(empty(), { ...sdk, version: [sdk.version] }), /version/);
  assert.throws(() => add(empty(), { ...sdk, protocolVersion: [sdk.protocolVersion] }), /protocol/);
  assert.throws(() => add(empty(), { ...sdk, channel: 'stable' }), /Unexpected/);
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
    assert.deepEqual(JSON.parse(readFileSync(path)).sdk.releases, [sdk]);
    assert.equal(run(['--validate', path]).status, 0);
    const before = readFileSync(path, 'utf8');
    for (const change of [{}, { VERSION: '3.8.0-RC1' }, { PRERELEASE: 'true' }]) {
      assert.equal(run([path], change).status, 0);
      assert.equal(readFileSync(path, 'utf8'), before);
    }
    assert.notEqual(run([path], { RELEASED_AT: 'invalid' }).status, 0);
    assert.equal(readFileSync(path, 'utf8'), before);
    assert.equal(run([path], { VERSION: '3.6.99' }).status, 0);
    assert.deepEqual(JSON.parse(readFileSync(path)).sdk.releases.map(r => r.version), ['3.7.0', '3.6.99']);
    writeFileSync(path, '{invalid');
    assert.notEqual(run().status, 0);
    assert.equal(readFileSync(path, 'utf8'), '{invalid');
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
});

test('SDK release submission dispatches generated compatibility values and fails without retries', () => {
  const temporary = mkdtempSync(join(tmpdir(), 'sdk-metadata-test-'));
  const script = fileURLToPath(new URL('./submit-sdk-release-metadata.sh', import.meta.url));
  const calls = join(temporary, 'calls.jsonl');
  const pom = join(temporary, 'parent.pom');
  const gh = join(temporary, 'gh');
  writeFileSync(gh, `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.appendFileSync(process.env.GH_CALLS, JSON.stringify(args) + '\\n');
if (args[0] === 'api') {
  if (process.env.FAIL_API) process.exit(17);
  console.log(process.env.RELEASE_JSON);
} else if (process.env.FAIL_DISPATCH) process.exit(23);
`);
  chmodSync(gh, 0o755);
  const release = { tag_name: 'v3.7.0', published_at: sdk.releasedAt, draft: false, prerelease: false };
  const env = {
    ...process.env, PATH: `${temporary}:${process.env.PATH}`, GH_CALLS: calls,
    GITHUB_REF_TYPE: 'tag', GITHUB_REF_NAME: 'v3.7.0', GITHUB_REPOSITORY: 'akka/akka-sdk',
    RELEASE_NOTES_URL: '',
    RELEASE_JSON: JSON.stringify(release), FAIL_API: '', FAIL_DISPATCH: '',
  };
  const run = (extra = {}) => {
    writeFileSync(calls, '');
    const result = spawnSync('bash', [script, pom], { env: { ...env, ...extra }, encoding: 'utf8' });
    return { ...result, calls: readFileSync(calls, 'utf8').trim().split('\n').filter(Boolean).map(line => JSON.parse(line)) };
  };
  try {
    writeFileSync(pom, '<properties>\n<akka-runtime.version>1.7.2</akka-runtime.version>\n<akka-protocol.version>1.1</akka-protocol.version>\n</properties>');
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.calls[0], ['api', 'repos/akka/akka-sdk/releases/tags/v3.7.0']);
    assert.deepEqual(result.calls[1], [
      'workflow', 'run', 'update-release-metadata.yml', '--repo', 'akka/akka-sdk', '--ref', 'main',
      '-f', 'component=sdk', '-f', 'version=3.7.0', '-f', `released_at=${sdk.releasedAt}`,
      '-f', 'prerelease=false', '-f', 'release_notes_url=',
      '-f', 'required_runtime=1.7.2', '-f', 'protocol_version=1.1',
    ]);
    const withNotes = run({ RELEASE_NOTES_URL: sdk.releaseNotesUrl });
    assert.equal(withNotes.status, 0, withNotes.stderr);
    assert.ok(withNotes.calls[1].includes(`release_notes_url=${sdk.releaseNotesUrl}`));
    for (const extra of [{ GITHUB_REF_TYPE: 'branch' }, { GITHUB_REF_NAME: 'v3.8.0-RC1' }, { GITHUB_REF_NAME: 'v3.8.0-SNAPSHOT' }]) {
      const skipped = run(extra);
      assert.equal(skipped.status, 0);
      assert.equal(skipped.calls.length, 0);
    }
    for (const flag of ['prerelease', 'draft']) {
      const skipped = run({ RELEASE_JSON: JSON.stringify({ ...release, [flag]: true }) });
      assert.equal(skipped.status, 0);
      assert.equal(skipped.calls.length, 1);
    }
    const failedApi = run({ FAIL_API: 'true' });
    assert.equal(failedApi.status, 17);
    assert.equal(failedApi.calls.length, 1);
    const failedDispatch = run({ FAIL_DISPATCH: 'true' });
    assert.equal(failedDispatch.status, 23);
    assert.equal(failedDispatch.calls.length, 2);
    writeFileSync(pom, '<properties><akka-runtime.version>REPLACED_BY_BUILD</akka-runtime.version></properties>');
    const invalid = run();
    assert.notEqual(invalid.status, 0);
    assert.equal(invalid.calls.length, 1);
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
});
