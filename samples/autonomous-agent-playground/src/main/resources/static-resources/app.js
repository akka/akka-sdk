// Playground UI shell. Phase 2 ships routing skeleton only; rendering is added in US1.

import { samples } from './samples/_registry.js';

const main = document.getElementById('app-main');

function parseRoute() {
  // /playground            → { view: 'landing' }
  // /playground/<sample>   → { view: 'panel', sampleId, runId: null }
  // /playground/<sample>/run/<runId> → { view: 'panel', sampleId, runId }
  const parts = location.pathname.replace(/^\/+/, '').split('/').filter(Boolean);
  if (parts[0] !== 'playground') return { view: 'landing' };
  if (parts.length === 1) return { view: 'landing' };
  const sampleId = parts[1];
  if (parts.length === 2) return { view: 'panel', sampleId, runId: null };
  if (parts.length === 4 && parts[2] === 'run') {
    return { view: 'panel', sampleId, runId: parts[3] };
  }
  return { view: 'notfound' };
}

function renderLanding() {
  // Phase 2 placeholder — US1 (T032) replaces this with a real listing.
  main.innerHTML = '<section><h1>Autonomous Agent Playground</h1><p>Loading samples…</p></section>';
}

function renderPanel(sampleId, runId) {
  // Phase 2 placeholder — US1 (T033) replaces this with the per-sample panel.
  const sample = samples[sampleId];
  if (!sample) {
    renderNotFound();
    return;
  }
  main.innerHTML = `<section><h1>${sample.displayName}</h1><p>Panel for ${sampleId}, run ${runId ?? '(new)'}.</p></section>`;
}

function renderNotFound() {
  // Phase 2 placeholder — US1 (T034) renders a minimal 404 view per FR-008b.
  main.innerHTML = '<section><h1>Not found</h1><p>The page or run you requested was not found.</p></section>';
}

function render() {
  const route = parseRoute();
  if (route.view === 'landing') return renderLanding();
  if (route.view === 'panel') return renderPanel(route.sampleId, route.runId);
  return renderNotFound();
}

window.addEventListener('popstate', render);

// Expose programmatic navigation for descriptors / future modules.
export function navigate(path) {
  history.pushState({}, '', path);
  render();
}

render();
