// Catalog rules and usage: release-metadata/README.md
import { isDeepStrictEqual } from 'node:util';

const components = ['sdk', 'runtime', 'cli'];
const versionPattern = /^\d+\.\d+\.\d+(\.\d+)?(-[A-Za-z0-9]+([.-][A-Za-z0-9]+)*)?$/;
const protocolPattern = /^\d+\.\d+$/;
const check = (condition, message) => {
  if (!condition) throw new Error(message);
};

function validateDate(value) {
  check(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(value), 'Expected a UTC timestamp (YYYY-MM-DDTHH:MM:SSZ)');
  const date = new Date(value);
  check(Number.isFinite(date.valueOf()) && date.toISOString().replace('.000Z', 'Z') === value, 'Invalid UTC timestamp');
}

function validateRelease(component, release) {
  check(components.includes(component), 'Unknown component');
  check(release && typeof release === 'object' && !Array.isArray(release), 'Missing release');
  check(versionPattern.test(release.version) && !release.version.includes('-'), 'Expected a stable numeric version without a tag prefix');
  check(!('channel' in release || 'releases' in release || 'latestStable' in release), 'Expected one release object per component');
  validateDate(release.releasedAt);
  check(typeof release.releaseNotesUrl === 'string' && /^https:\/\/doc\.akka\.io\/[^\s]+$/.test(release.releaseNotesUrl), 'Release notes must use https://doc.akka.io/');
  const url = new URL(release.releaseNotesUrl);
  check(!url.username && !url.password && !url.port, 'Invalid release-notes URL');
  if (component === 'sdk') {
    check(typeof release.requiredRuntime === 'string' && versionPattern.test(release.requiredRuntime) && !/snapshot/i.test(release.requiredRuntime), 'SDK required Runtime must be a release version');
    check(protocolPattern.test(release.protocolVersion), 'SDK protocol must be major.minor');
    check(release.supportedProtocol === undefined, 'SDK cannot set Runtime protocol range');
  } else {
    check(release.requiredRuntime === undefined && release.protocolVersion === undefined, 'Only SDK releases can set SDK compatibility fields');
  }
  if (component === 'runtime') {
    const { minimum, maximum } = release.supportedProtocol ?? {};
    check(protocolPattern.test(minimum) && protocolPattern.test(maximum), 'Runtime protocol range must be major.minor');
    const [minMajor, minMinor] = minimum.split('.').map(BigInt);
    const [maxMajor, maxMinor] = maximum.split('.').map(BigInt);
    check(minMajor === maxMajor && minMinor <= maxMinor, 'Invalid Runtime protocol range');
  } else if (component === 'cli') {
    check(release.supportedProtocol === undefined, 'CLI cannot set compatibility fields');
  }
}

export function releaseFromEnvironment(env) {
  check(components.includes(env.COMPONENT), 'Unknown component');
  check(typeof env.VERSION === 'string' && versionPattern.test(env.VERSION), 'Invalid version (omit the tag prefix)');
  check(['true', 'false', undefined, ''].includes(env.PRERELEASE), 'PRERELEASE must be true or false');
  if (env.PRERELEASE === 'true' || env.VERSION.includes('-')) return null;
  const component = env.COMPONENT;
  const release = {
    version: env.VERSION, releasedAt: env.RELEASED_AT,
    releaseNotesUrl: env.RELEASE_NOTES_URL,
  };
  if (component === 'sdk') {
    check(!env.MINIMUM_PROTOCOL && !env.MAXIMUM_PROTOCOL, 'SDK cannot set Runtime protocol range');
    release.requiredRuntime = env.REQUIRED_RUNTIME;
    release.protocolVersion = env.PROTOCOL_VERSION;
  } else {
    check(!env.REQUIRED_RUNTIME && !env.PROTOCOL_VERSION, 'Only SDK releases can set SDK compatibility fields');
    if (component === 'runtime') {
      release.supportedProtocol = { minimum: env.MINIMUM_PROTOCOL, maximum: env.MAXIMUM_PROTOCOL };
    } else {
      check(!env.MINIMUM_PROTOCOL && !env.MAXIMUM_PROTOCOL, 'CLI cannot set compatibility fields');
    }
  }
  validateRelease(component, release);
  return { component, release };
}

export function compareVersions(left, right) {
  const a = left.split('.').map(BigInt), b = right.split('.').map(BigInt);
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    if (a[i] !== b[i]) return a[i] > b[i] ? 1 : -1;
  }
  return Math.sign(a.length - b.length);
}

export function validateCatalog(catalog) {
  check(catalog?.schemaVersion === 1, 'Unsupported schema version');
  if (catalog.generatedAt !== null) validateDate(catalog.generatedAt);
  for (const component of components) {
    // Null represents a component whose first real release has not been submitted.
    if (catalog[component] !== null) validateRelease(component, catalog[component]);
  }
}

export function updateCatalog(catalog, component, release, now = new Date()) {
  validateCatalog(catalog);
  validateRelease(component, release);
  if (catalog[component] && compareVersions(release.version, catalog[component].version) < 0) return catalog;
  if (isDeepStrictEqual(catalog[component], release)) return catalog;
  const updated = {
    ...catalog,
    [component]: structuredClone(release),
    generatedAt: now.toISOString().replace(/\.\d{3}Z$/, 'Z'),
  };
  validateCatalog(updated);
  return updated;
}
