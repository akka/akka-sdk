// Playground UI shell. US1: landing list, per-sample panel, status polling, result rendering,
// failure rendering, in-app 404. SSE event log + theme toggle land in US2 / US3.

import { samples } from '/playground/static/samples/_registry.js';

const main = document.getElementById('app-main');
const header = document.getElementById('app-header');

const RUN_STORE_KEY_PREFIX = 'playground-run-';
const POLL_INTERVAL_MS = 2000;
const TERMINAL_STATES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

let activePoll = null;

// --- Run cache (per-browser-tab; survives reload via sessionStorage) ---

function cacheRun(runId, data) {
  try {
    sessionStorage.setItem(RUN_STORE_KEY_PREFIX + runId, JSON.stringify(data));
  } catch (e) {
    // sessionStorage may be unavailable; ignore.
  }
}

function loadCachedRun(runId) {
  try {
    const raw = sessionStorage.getItem(RUN_STORE_KEY_PREFIX + runId);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

// --- Routing ---

function parseRoute() {
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

export function navigate(path) {
  history.pushState({}, '', path);
  render();
}

function attachLinkHandlers(root) {
  root.querySelectorAll('a[data-link]').forEach((a) => {
    a.addEventListener('click', (e) => {
      e.preventDefault();
      navigate(a.getAttribute('href'));
    });
  });
}

// --- Header ---

function renderHeader() {
  header.innerHTML = `
    <div class="header-bar">
      <a href="/playground" class="brand" data-link>Autonomous Agent Playground</a>
    </div>
  `;
  attachLinkHandlers(header);
}

// --- Landing ---

async function renderLanding() {
  cancelPoll();
  let list;
  try {
    const resp = await fetch('/playground/api/samples');
    if (!resp.ok) throw new Error('failed');
    list = await resp.json();
  } catch (e) {
    main.innerHTML = `<section><p role="alert">Could not load samples list.</p></section>`;
    return;
  }

  const rows = list.samples
    .map((s) => {
      const desc = samples[s.id];
      const overview = desc?.description?.overview ?? '';
      return `
        <li class="sample-row">
          <a href="/playground/${s.id}" data-link>
            <h3>${escapeHtml(s.displayName)}</h3>
            <p>${escapeHtml(overview)}</p>
          </a>
        </li>
      `;
    })
    .join('');

  main.innerHTML = `
    <section>
      <h1>Samples</h1>
      <p class="muted">Pick a sample to run an autonomous agent.</p>
      <ul class="sample-list">${rows}</ul>
    </section>
  `;
  attachLinkHandlers(main);
}

// --- Description rendering (FR-002a) ---

function renderDescription(d) {
  if (!d) return '';
  const agents = (d.agents || []).map((a) => `<li>${escapeHtml(a)}</li>`).join('');
  const tasks = (d.tasks || []).map((t) => `<li>${escapeHtml(t)}</li>`).join('');
  return `
    <details class="sample-description" open>
      <summary><strong>About this sample</strong></summary>
      <p>${escapeHtml(d.overview ?? '')}</p>
      ${agents ? `<h4>Agents</h4><ul>${agents}</ul>` : ''}
      ${tasks ? `<h4>Tasks</h4><ul>${tasks}</ul>` : ''}
      ${d.flow ? `<h4>Flow</h4><p>${escapeHtml(d.flow)}</p>` : ''}
      ${d.demonstrates ? `<h4>Demonstrates</h4><p>${escapeHtml(d.demonstrates)}</p>` : ''}
    </details>
  `;
}

// --- Input form ---

function renderInputForm(descriptor, container) {
  const fieldsHtml = descriptor.inputForm.fields
    .map((f) => {
      const id = `input-${f.name}`;
      const label = `<label for="${id}">${escapeHtml(f.label)}</label>`;
      if (f.type === 'textarea') {
        return `${label}<textarea id="${id}" name="${f.name}" rows="${f.rows ?? 6}" placeholder="${escapeHtml(f.placeholder ?? '')}"></textarea>`;
      }
      if (f.type === 'select') {
        const opts = (f.options || [])
          .map((o) => `<option value="${o.value}">${escapeHtml(o.label)}</option>`)
          .join('');
        return `${label}<select id="${id}" name="${f.name}">${opts}</select>`;
      }
      return `${label}<input type="text" id="${id}" name="${f.name}" placeholder="${escapeHtml(f.placeholder ?? '')}" />`;
    })
    .join('');

  container.innerHTML = `
    <form class="run-form">
      ${fieldsHtml}
      <button type="submit">Run</button>
    </form>
  `;

  const form = container.querySelector('form');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitButton = form.querySelector('button');
    submitButton.disabled = true;
    submitButton.setAttribute('aria-busy', 'true');
    const data = Object.fromEntries(new FormData(form).entries());
    try {
      const submitted = await descriptor.submit(data);
      cacheRun(submitted.runId, {
        sampleId: descriptor.id,
        agentComponentId: submitted.agentComponentId,
        taskId: submitted.taskId,
        extras: submitted.extras ?? {},
      });
      navigate(`/playground/${descriptor.id}/run/${submitted.runId}`);
    } catch (err) {
      submitButton.disabled = false;
      submitButton.removeAttribute('aria-busy');
      const error = document.createElement('p');
      error.setAttribute('role', 'alert');
      error.textContent = `Submission failed: ${err.message ?? err}`;
      form.appendChild(error);
    }
  });
}

// --- Run view ---

function renderRunView(descriptor, runId, runCache) {
  const wrap = document.createElement('section');
  wrap.innerHTML = `
    <h1>${escapeHtml(descriptor.displayName)}</h1>
    ${renderDescription(descriptor.description)}
    <div class="run-status">
      <span class="status-badge" data-state="PENDING">PENDING</span>
      <span class="status-detail muted"></span>
    </div>
    <div class="run-failure" hidden></div>
    <div class="run-actions"></div>
    <div class="run-result"></div>
  `;
  main.replaceChildren(wrap);

  const badge = wrap.querySelector('.status-badge');
  const detail = wrap.querySelector('.status-detail');
  const failureBlock = wrap.querySelector('.run-failure');
  const actions = wrap.querySelector('.run-actions');
  const resultBlock = wrap.querySelector('.run-result');

  function setState(state, statusJson) {
    badge.textContent = state;
    badge.dataset.state = state;
    if (state === 'AWAITING_INPUT') {
      detail.textContent = 'Awaiting operator input';
    } else if (state === 'RUNNING') {
      detail.textContent = 'Agent is working…';
    } else if (state === 'PENDING') {
      detail.textContent = 'Queued.';
    } else {
      detail.textContent = '';
    }
    if (state === 'FAILED' || state === 'CANCELLED') {
      failureBlock.hidden = false;
      failureBlock.innerHTML = `<h3>${state === 'CANCELLED' ? 'Cancelled' : 'Failed'}</h3>
        <p>${escapeHtml(statusJson?.failureReason ?? 'No reason reported.')}</p>`;
    } else {
      failureBlock.hidden = true;
    }
    if (state === 'COMPLETED' && statusJson?.finalResult) {
      resultBlock.innerHTML = '';
      resultBlock.appendChild(descriptor.renderResult(statusJson.finalResult, statusJson));
    }
    if (descriptor.renderActions) {
      actions.innerHTML = '';
      const node = descriptor.renderActions(state, statusJson, runId, runCache);
      if (node) actions.appendChild(node);
    }
  }

  setState('PENDING', null);
  pollLoop(descriptor, runId, runCache, setState);
}

async function pollLoop(descriptor, runId, runCache, setState) {
  cancelPoll();
  let cancelled = false;
  activePoll = { cancel: () => { cancelled = true; } };

  const url = `/playground/api/runs/${encodeURIComponent(runId)}/status?component=${encodeURIComponent(runCache.agentComponentId)}&task=${encodeURIComponent(runCache.taskId)}&sample=${encodeURIComponent(descriptor.id)}`;

  while (!cancelled) {
    try {
      const resp = await fetch(url);
      if (resp.status === 404) {
        renderInAppNotFound();
        return;
      }
      if (!resp.ok) throw new Error('status fetch failed');
      const json = await resp.json();
      let runState = json.runState;
      if (descriptor.overrideRunState) {
        runState = (await descriptor.overrideRunState(runState, json, runCache)) ?? runState;
      }
      setState(runState, json);
      if (TERMINAL_STATES.has(runState)) return;
    } catch (e) {
      // Transient — keep polling.
    }
    await sleep(POLL_INTERVAL_MS);
  }
}

function cancelPoll() {
  if (activePoll) {
    activePoll.cancel();
    activePoll = null;
  }
}

// --- Per-sample panel dispatch ---

function renderPanel(sampleId, runId) {
  cancelPoll();
  const descriptor = samples[sampleId];
  if (!descriptor) {
    renderNotFound();
    return;
  }
  if (runId === null) {
    const wrap = document.createElement('section');
    wrap.innerHTML = `
      <h1>${escapeHtml(descriptor.displayName)}</h1>
      ${renderDescription(descriptor.description)}
      <div class="run-form-host"></div>
    `;
    main.replaceChildren(wrap);
    renderInputForm(descriptor, wrap.querySelector('.run-form-host'));
    return;
  }

  const runCache = loadCachedRun(runId);
  if (!runCache) {
    // Cross-tab share or post-restart — task ids not cached locally. Surface a 404 view.
    renderInAppNotFound();
    return;
  }
  renderRunView(descriptor, runId, runCache);
}

function renderNotFound() {
  cancelPoll();
  main.innerHTML = `
    <section>
      <h1>Not found</h1>
      <p>The page or run you requested was not found.</p>
      <p><a href="/playground" data-link>Back to samples</a></p>
    </section>
  `;
  attachLinkHandlers(main);
}

function renderInAppNotFound() {
  renderNotFound();
}

// --- Utilities ---

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

export function escapeHtml(s) {
  if (s == null) return '';
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

// --- Bootstrap ---

function render() {
  renderHeader();
  const route = parseRoute();
  if (route.view === 'landing') return renderLanding();
  if (route.view === 'panel') return renderPanel(route.sampleId, route.runId);
  return renderNotFound();
}

window.addEventListener('popstate', render);
render();
