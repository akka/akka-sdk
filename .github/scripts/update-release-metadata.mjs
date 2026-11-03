#!/usr/bin/env node
// Usage and release metadata contract: release-metadata/README.md
import { readFile, writeFile } from 'node:fs/promises';
import { isDeepStrictEqual } from 'node:util';
import { releaseFromEnvironment, updateCatalog, validateCatalog } from './release-metadata.mjs';

async function main() {
  const args = process.argv.slice(2);
  const validateOnly = args[0] === '--validate';
  if (validateOnly) args.shift();
  if (args.length > 1) throw new Error('Usage: update-release-metadata.mjs [--validate] [catalog]');
  const path = args[0] ?? 'release-metadata/versions.json';
  const catalog = JSON.parse(await readFile(path, 'utf8'));
  validateCatalog(catalog);
  if (validateOnly) {
    console.log('Catalog valid');
    return;
  }
  const { component, release } = releaseFromEnvironment(process.env, catalog);
  const updated = updateCatalog(catalog, component, release);
  if (isDeepStrictEqual(updated, catalog)) {
    console.log('Metadata unchanged');
    return;
  }
  await writeFile(path, `${JSON.stringify(updated, null, 2)}\n`);
  console.log(`Updated ${component} metadata for ${release.version}`);
}

main().catch(error => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});
