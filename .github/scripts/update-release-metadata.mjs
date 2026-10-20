#!/usr/bin/env node
// Usage and release metadata contract: release-metadata/README.md
import { readFile, mkdtemp, writeFile, rename, rm } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { releaseFromEnvironment, updateCatalog, validateCatalog } from './release-metadata.mjs';

async function main() {
  const args = process.argv.slice(2);
  const validateOnly = args[0] === '--validate';
  if (validateOnly) args.shift();
  if (args.length > 1) throw new Error('Usage: update-release-metadata.mjs [--validate] [catalog]');
  const path = args[0] ?? 'release-metadata/versions.json';
  const submission = validateOnly ? null : releaseFromEnvironment(process.env);
  if (!validateOnly && !submission) {
    console.log('Ignoring prerelease or snapshot');
    return;
  }
  const catalog = JSON.parse(await readFile(path, 'utf8'));
  if (validateOnly) {
    validateCatalog(catalog);
    console.log('Catalog valid');
    return;
  }
  const updated = updateCatalog(catalog, submission.component, submission.release);
  if (updated === catalog) {
    console.log('Metadata unchanged');
    return;
  }
  // Rename within the same directory so readers never see a partial JSON file.
  const temporary = await mkdtemp(join(dirname(path), '.release-metadata-'));
  try {
    const staged = join(temporary, 'versions.json');
    await writeFile(staged, `${JSON.stringify(updated, null, 2)}\n`);
    await rename(staged, path);
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
  console.log(`Updated ${submission.component} metadata for ${submission.release.version}`);
}

main().catch(error => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});
