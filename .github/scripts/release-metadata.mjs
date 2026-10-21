// Catalog rules and usage: release-metadata/README.md
const components = ['sdk', 'runtime', 'cli'];
const defaultReleaseNotesUrl = 'https://doc.akka.io/reference/release-notes.html';
const stablePattern = /^\d+(\.\d+){2,3}$/;
// Runtime versions can carry a qualifier; snapshots are rejected separately.
const runtimePattern = /^\d+\.\d+\.\d+(-[A-Za-z0-9]+([.-][A-Za-z0-9]+)*)?$/;
const protocolPattern = /^\d+\.\d+$/;
const check = (condition, message) => {
  if (!condition) throw new Error(message);
};

const toUtc = date => date.toISOString().replace('.000Z', 'Z');

function validateDate(value) {
  const date = new Date(value);
  check(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(value) && Number.isFinite(date.valueOf()) && toUtc(date) === value,
    'Expected a valid UTC timestamp (YYYY-MM-DDTHH:MM:SSZ)');
}

function validateUrl(value) {
  check(typeof value === 'string', 'Release notes URL must be a string');
  let url;
  try { url = new URL(value); } catch { /* reported below */ }
  check(url?.protocol === 'https:' && url.hostname === 'doc.akka.io' && !url.username && !url.password && !url.port, 'Release notes must use https://doc.akka.io/');
}

function validateRelease(component, release) {
  check(components.includes(component), 'Unknown component');
  check(release && typeof release === 'object' && !Array.isArray(release), 'Missing release');
  check(typeof release.version === 'string' && stablePattern.test(release.version), 'Expected a stable numeric version without a tag prefix');
  validateDate(release.releasedAt);
  validateUrl(release.releaseNotesUrl);
  if (component === 'sdk') {
    check(typeof release.requiredRuntime === 'string' && runtimePattern.test(release.requiredRuntime) && !/snapshot/i.test(release.requiredRuntime), 'SDK required Runtime must be a release version');
    check(typeof release.protocolVersion === 'string' && protocolPattern.test(release.protocolVersion), 'SDK protocol must be major.minor');
    check(release.supportedProtocol === undefined, 'SDK cannot set Runtime protocol range');
    validateDate(release.notifyAt);
  } else {
    check(release.requiredRuntime === undefined && release.protocolVersion === undefined, 'Only SDK releases can set SDK compatibility fields');
    check(release.notifyAt === undefined, 'Only SDK releases can set notifyAt');
  }
  if (component === 'runtime') {
    const { minimum, maximum } = release.supportedProtocol ?? {};
    check(typeof minimum === 'string' && typeof maximum === 'string' && protocolPattern.test(minimum) && protocolPattern.test(maximum), 'Runtime protocol range must be major.minor');
    const [minMajor, minMinor] = minimum.split('.').map(Number);
    const [maxMajor, maxMinor] = maximum.split('.').map(Number);
    check(minMajor === maxMajor && minMinor <= maxMinor, 'Invalid Runtime protocol range');
  } else if (component === 'cli') {
    check(release.supportedProtocol === undefined, 'CLI cannot set compatibility fields');
  }
}

export function releaseFromEnvironment(env, catalog) {
  check(components.includes(env.COMPONENT), 'Unknown component');
  check(typeof env.VERSION === 'string' && stablePattern.test(env.VERSION), 'Invalid version (stable numeric version, omit the tag prefix)');
  const component = env.COMPONENT;
  const existing = catalog?.[component]?.releases.find(item => item.version === env.VERSION);
  const release = {
    ...existing,
    version: env.VERSION, releasedAt: env.RELEASED_AT || existing?.releasedAt,
    releaseNotesUrl: env.RELEASE_NOTES_URL || existing?.releaseNotesUrl || defaultReleaseNotesUrl,
  };
  if (component === 'sdk') {
    check(!env.MINIMUM_PROTOCOL && !env.MAXIMUM_PROTOCOL, 'SDK cannot set Runtime protocol range');
    release.requiredRuntime = env.REQUIRED_RUNTIME || existing?.requiredRuntime;
    release.protocolVersion = env.PROTOCOL_VERSION || existing?.protocolVersion;
    validateDate(release.releasedAt);
    const previous = catalog?.sdk.releases.find(item => compareVersions(item.version, release.version) < 0);
    if (!env.NOTIFY_DELAY_DAYS && existing) {
      release.notifyAt = existing.notifyAt;
    } else if (!env.NOTIFY_DELAY_DAYS && previous?.requiredRuntime === release.requiredRuntime) {
      // Copy the date even if it has passed: the preceding Runtime requirement
      // is already eligible, so this release needs no additional waiting period.
      release.notifyAt = previous.notifyAt;
    } else {
      const delay = env.NOTIFY_DELAY_DAYS || '10';
      check(/^\d{1,4}$/.test(delay), 'SDK notification delay must be a whole number of days (0 to 9999)');
      release.notifyAt = toUtc(new Date(Date.parse(release.releasedAt) + Number(delay) * 86400000));
    }
  } else {
    check(!env.REQUIRED_RUNTIME && !env.PROTOCOL_VERSION, 'Only SDK releases can set SDK compatibility fields');
    if (component === 'runtime') {
      release.supportedProtocol = {
        minimum: env.MINIMUM_PROTOCOL || existing?.supportedProtocol.minimum,
        maximum: env.MAXIMUM_PROTOCOL || existing?.supportedProtocol.maximum,
      };
    } else {
      check(!env.MINIMUM_PROTOCOL && !env.MAXIMUM_PROTOCOL, 'CLI cannot set compatibility fields');
    }
  }
  validateRelease(component, release);
  return { component, release };
}

export function compareVersions(left, right) {
  const a = left.split('.').map(Number), b = right.split('.').map(Number);
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    if (a[i] !== b[i]) return a[i] > b[i] ? 1 : -1;
  }
  return Math.sign(a.length - b.length);
}

export function validateCatalog(catalog) {
  check(catalog?.schemaVersion === 1, 'Unsupported schema version');
  for (const component of components) {
    const releases = catalog[component]?.releases;
    check(Array.isArray(releases), `Expected ${component}.releases array`);
    const versions = new Set();
    for (const release of releases) {
      validateRelease(component, release);
      check(!versions.has(release.version), `Duplicate ${component} version ${release.version}`);
      versions.add(release.version);
    }
    for (let i = 1; i < releases.length; i++) {
      check(compareVersions(releases[i - 1].version, releases[i].version) > 0, `${component} releases must be ordered newest first`);
    }
  }
}

export function updateCatalog(catalog, component, release) {
  validateRelease(component, release);
  const releases = catalog[component].releases
    .filter(item => item.version !== release.version)
    .concat(structuredClone(release))
    .sort((a, b) => compareVersions(b.version, a.version));
  const updated = { ...catalog, [component]: { ...catalog[component], releases } };
  validateCatalog(updated);
  return updated;
}
