// Playground UI shell. US1: landing + panel + status polling + result + 404.
// US2: live SSE event log, three-tier rows, run summary, stuck notice, connection status, stop.

import { samples } from '/playground/static/samples/_registry.js';
import { el, escapeHtml, postJson } from '/playground/static/samples/_helpers.js';
import { AgentDisplay, connectEventStream, renderEventRow } from '/playground/static/event-log.js';
import { RunSummary } from '/playground/static/run-summary.js';
import { renderThemeToggle } from '/playground/static/theme.js';

const main = document.getElementById('app-main');
const header = document.getElementById('app-header');

const RUN_STORE_KEY_PREFIX = 'playground-run-';
const STATUS_REFRESH_MS = 5000;             // backstop poll while SSE is connected
const STUCK_THRESHOLD_MS = 60_000;          // FR-011a
const TERMINAL_STATES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

let activeRun = null; // { cancel: fn }

// --- Run cache (per-browser-tab; survives reload via sessionStorage) ---

function cacheRun(runId, data) {
  try {
    sessionStorage.setItem(RUN_STORE_KEY_PREFIX + runId, JSON.stringify(data));
  } catch (e) {}
}

function loadCachedRun(runId) {
  try {
    const raw = sessionStorage.getItem(RUN_STORE_KEY_PREFIX + runId);
    return raw ? JSON.parse(raw) : null;
  } catch (e) { return null; }
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
      <div class="theme-toggle-host"></div>
    </div>
  `;
  attachLinkHandlers(header);
  renderThemeToggle(header.querySelector('.theme-toggle-host'));
}

// --- Landing ---

async function renderLanding() {
  cancelActiveRun();
  let list;
  try {
    const resp = await fetch('/playground/api/samples');
    if (!resp.ok) throw new Error('failed');
    list = await resp.json();
  } catch (e) {
    main.innerHTML = `<section><p role="alert">Could not load samples list.</p></section>`;
    return;
  }

  const rows = list.samples.map((s) => {
    const desc = samples[s.id];
    const overview = desc?.description?.overview ?? '';
    return `
      <li class="sample-row">
        <a href="/playground/${s.id}" data-link>
          <h3>${escapeHtml(s.displayName)}</h3>
          <p>${escapeHtml(overview)}</p>
        </a>
      </li>`;
  }).join('');

  main.innerHTML = `
    <section>
      <h1>Samples</h1>
      <p class="muted">Pick a sample to run an autonomous agent.</p>
      <ul class="sample-list">${rows}</ul>
    </section>`;
  attachLinkHandlers(main);
}

// --- Description (FR-002a) ---

function renderDescription(d) {
  if (!d) return '';
  const agents = (d.agents || []).map((a) => `<li>${escapeHtml(a)}</li>`).join('');
  const tasks = (d.tasks || []).map((t) => `<li>${escapeHtml(t)}</li>`).join('');
  return `
    <details class="sample-description">
      <summary><strong>About this sample</strong></summary>
      <p>${escapeHtml(d.overview ?? '')}</p>
      ${agents ? `<h4>Agents</h4><ul>${agents}</ul>` : ''}
      ${tasks ? `<h4>Tasks</h4><ul>${tasks}</ul>` : ''}
      ${d.flow ? `<h4>Flow</h4><p>${escapeHtml(d.flow)}</p>` : ''}
      ${d.demonstrates ? `<h4>Demonstrates</h4><p>${escapeHtml(d.demonstrates)}</p>` : ''}
    </details>`;
}

// --- Input form ---

function renderInputForm(descriptor, container) {
  const fieldsHtml = descriptor.inputForm.fields.map((f) => {
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
  }).join('');

  container.innerHTML = `<form class="run-form">${fieldsHtml}<button type="submit">Run</button></form>`;
  const form = container.querySelector('form');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(form).entries());
    // Pre-generate the runId client-side so the SSE stream can subscribe before the agent
    // activates (otherwise early lifecycle events are lost — notificationStream is live-only).
    const runId = crypto.randomUUID
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    // Cache a placeholder; the run view will fill in taskId once submit returns.
    cacheRun(runId, {
      sampleId: descriptor.id,
      agentComponentId: descriptor.agentComponentId,
      taskId: null,
      extras: {},
      pendingValues: data,
    });
    navigate(`/playground/${descriptor.id}/run/${runId}`);
  });
}

// --- Run view (US2) ---

function renderRunView(descriptor, runId, runCache) {
  const wrap = el('section');
  wrap.innerHTML = `
    <h1>${escapeHtml(descriptor.displayName)}</h1>
    ${renderDescription(descriptor.description)}
    <div class="run-meta">
      <div class="run-status">
        <span class="status-badge" data-state="PENDING">PENDING</span>
        <span class="status-detail muted"></span>
        <span class="connection-indicator" data-connection="connecting" hidden></span>
      </div>
      <div class="run-summary"></div>
    </div>
    <div class="stuck-notice" hidden></div>
    <div class="run-control"></div>
    <div class="run-failure" hidden></div>
    <div class="run-actions"></div>
    <div class="run-result"></div>
    <section class="run-events">
      <h3>Events</h3>
      <ol class="event-log" aria-live="polite"></ol>
    </section>
  `;
  main.replaceChildren(wrap);

  const badge = wrap.querySelector('.status-badge');
  const detail = wrap.querySelector('.status-detail');
  const connectionEl = wrap.querySelector('.connection-indicator');
  const summaryHost = wrap.querySelector('.run-summary');
  const stuckEl = wrap.querySelector('.stuck-notice');
  const controlEl = wrap.querySelector('.run-control');
  const failureBlock = wrap.querySelector('.run-failure');
  const actions = wrap.querySelector('.run-actions');
  const resultBlock = wrap.querySelector('.run-result');
  const eventLog = wrap.querySelector('.event-log');

  const summary = new RunSummary(summaryHost);
  const agentDisplay = new AgentDisplay();
  let lastEventReceivedAt = Date.now();
  let currentState = 'PENDING';
  let currentStatusJson = null;

  // Connection-status indicator (FR-009b)
  function setConnection(state) {
    connectionEl.hidden = state === 'connected';
    connectionEl.dataset.connection = state;
    connectionEl.textContent =
      state === 'reconnecting' ? 'reconnecting…' :
      state === 'disconnected' ? 'live updates unavailable' : '';
  }

  function setState(state, statusJson) {
    currentState = state;
    if (statusJson) currentStatusJson = statusJson;
    badge.textContent = state;
    badge.dataset.state = state;
    if (state === 'AWAITING_INPUT') detail.textContent = 'Awaiting operator input';
    else if (state === 'RUNNING') detail.textContent = 'Agent is working…';
    else if (state === 'PENDING') detail.textContent = 'Queued.';
    else detail.textContent = '';

    if (state === 'FAILED' || state === 'CANCELLED') {
      failureBlock.hidden = false;
      failureBlock.innerHTML = `<h3>${state === 'CANCELLED' ? 'Cancelled' : 'Failed'}</h3>
        <p>${escapeHtml(currentStatusJson?.failureReason ?? 'No reason reported.')}</p>`;
    } else {
      failureBlock.hidden = true;
    }
    if (state === 'COMPLETED' && currentStatusJson?.finalResult) {
      resultBlock.replaceChildren(descriptor.renderResult(currentStatusJson.finalResult, currentStatusJson));
    }
    if (descriptor.renderActions) {
      actions.innerHTML = '';
      const node = descriptor.renderActions(state, currentStatusJson, runId, runCache, {
        // Operator-action handlers can call this after the POST returns to flip the panel
        // immediately (otherwise the user waits up to STATUS_REFRESH_MS for the next poll).
        refreshNow: () => refreshStatus().catch(() => {}),
      });
      if (node) actions.appendChild(node);
    }
    renderControl(state);
  }

  // Stop control + confirmation (FR-013a). Visible only while non-terminal.
  function renderControl(state) {
    controlEl.innerHTML = '';
    if (TERMINAL_STATES.has(state)) return;
    const stopBtn = el('button', {
      type: 'button',
      className: 'stop-button',
      onClick: () => confirmAndStop(),
    }, 'Stop');
    controlEl.appendChild(stopBtn);
  }

  async function confirmAndStop() {
    const dialog = el('dialog', { className: 'confirm-dialog' });
    dialog.innerHTML = `
      <h3>Stop this run?</h3>
      <p>The agent will be sent an operator stop signal.</p>
      <div class="dialog-buttons">
        <button type="button" data-action="cancel">Cancel</button>
        <button type="button" data-action="confirm" class="danger">Stop</button>
      </div>
    `;
    document.body.appendChild(dialog);
    dialog.showModal();
    const decision = await new Promise((resolve) => {
      dialog.querySelector('[data-action="cancel"]').addEventListener('click', () => resolve(false));
      dialog.querySelector('[data-action="confirm"]').addEventListener('click', () => resolve(true));
    });
    dialog.close();
    dialog.remove();
    if (!decision) return;
    try {
      await postJson(
        `/playground/api/runs/${encodeURIComponent(runId)}/stop?component=${encodeURIComponent(runCache.agentComponentId)}`,
        {}
      );
      // The actual state transition arrives via the Stopped notification on SSE.
    } catch (err) {
      // Surface a passive notice; the user can retry.
      const e = el('p', { role: 'alert' }, `Stop failed: ${err.message ?? err}`);
      controlEl.appendChild(e);
    }
  }

  // Stuck-run notice (FR-011a). 60s of no events while non-terminal.
  const stuckTimer = setInterval(() => {
    if (TERMINAL_STATES.has(currentState)) {
      stuckEl.hidden = true;
      return;
    }
    const silentFor = Date.now() - lastEventReceivedAt;
    if (silentFor > STUCK_THRESHOLD_MS) {
      stuckEl.hidden = false;
      stuckEl.textContent = `No recent activity for over a minute. The agent may be working on a long iteration; the run is not declared failed.`;
    } else {
      stuckEl.hidden = true;
    }
  }, 1000);

  // Backstop status fetch — covers the run-state for terminal outcomes regardless of SSE health.
  async function refreshStatus() {
    if (!runCache.taskId) return; // submit hasn't returned yet
    const url = `/playground/api/runs/${encodeURIComponent(runId)}/status?component=${encodeURIComponent(runCache.agentComponentId)}&task=${encodeURIComponent(runCache.taskId)}&sample=${encodeURIComponent(descriptor.id)}`;
    const resp = await fetch(url);
    if (resp.status === 404) {
      cancel();
      renderInAppNotFound();
      return;
    }
    if (!resp.ok) return;
    const json = await resp.json();
    let runState = json.runState;
    if (descriptor.overrideRunState) {
      runState = (await descriptor.overrideRunState(runState, json, runCache)) ?? runState;
    }
    setState(runState, json);
  }

  let statusTimer = null;
  function startStatusBackstop() {
    if (statusTimer) return;
    statusTimer = setInterval(() => {
      if (TERMINAL_STATES.has(currentState)) {
        clearInterval(statusTimer);
        statusTimer = null;
        return;
      }
      refreshStatus().catch(() => {});
    }, STATUS_REFRESH_MS);
  }

  // Connect SSE.
  setConnection('connecting');
  let pendingSubmitDone = !runCache.pendingValues; // true means status already exists
  const closeStream = connectEventStream(runId, runCache.agentComponentId, {
    onOpen: async () => {
      setConnection('connected');
      // First open: if a pending submit was queued by the form, fire the actual POST now —
      // SSE is live so we won't lose the early lifecycle events (FR-009 timing).
      if (!pendingSubmitDone && runCache.pendingValues) {
        pendingSubmitDone = true;
        try {
          const submitted = await descriptor.submit(runCache.pendingValues, { runId });
          runCache.taskId = submitted.taskId;
          runCache.extras = submitted.extras ?? runCache.extras;
          runCache.pendingValues = null;
          cacheRun(runId, runCache);
          refreshStatus().catch(() => {});
          // Start the status backstop poll. Essential for samples whose owning agent stops
          // mid-pipeline (e.g. publishing's ContentAgent stops after DRAFT, but the PUBLISH
          // task runs on PublishingAgent — a different instance whose SSE we don't subscribe
          // to). The poll is what flips the panel to COMPLETED in those cases.
          startStatusBackstop();
        } catch (err) {
          failureBlock.hidden = false;
          failureBlock.innerHTML = `<h3>Submission failed</h3><p>${escapeHtml(err?.message ?? String(err))}</p>`;
        }
      } else if (runCache.taskId) {
        // Reconnect on an existing run — recover any terminal outcome we may have missed.
        refreshStatus().catch(() => {});
      }
    },
    onError: () => setConnection('reconnecting'),
    onEnvelope: (envelope) => {
      lastEventReceivedAt = Date.now();
      // Auto-scroll to follow new events, but only if the user is already at the bottom.
      // If they've scrolled up to read older rows, don't yank them back.
      const slack = 8; // pixels of tolerance
      const wasAtBottom =
        eventLog.scrollHeight - eventLog.scrollTop - eventLog.clientHeight <= slack;
      const row = renderEventRow(envelope, agentDisplay);
      eventLog.appendChild(row);
      if (wasAtBottom) eventLog.scrollTop = eventLog.scrollHeight;
      summary.ingest(envelope);

      // Operator stop is observed only on the SSE stream (the SDK doesn't expose the stop
      // reason via getState()); reflect it locally without waiting for the next status fetch.
      if (envelope.kind === 'Stopped' && envelope.raw?.reason === 'operator') {
        setState('CANCELLED', {
          ...currentStatusJson,
          runState: 'CANCELLED',
          failureReason: 'Stopped by operator',
        });
        return;
      }
      // Other terminal task transitions reconcile via a status fetch.
      if (envelope.kind === 'TaskCompleted' || envelope.kind === 'TaskFailed' || envelope.kind === 'TaskCancelled') {
        refreshStatus().catch(() => {});
      }
    },
  });

  // If the run is already submitted (e.g. user opens a shared run URL), fetch status now.
  if (runCache.taskId) {
    refreshStatus().catch(() => {});
    startStatusBackstop();
  }

  function cancel() {
    closeStream();
    clearInterval(stuckTimer);
    if (statusTimer) clearInterval(statusTimer);
  }

  activeRun = { cancel };
}

function cancelActiveRun() {
  if (activeRun) {
    activeRun.cancel();
    activeRun = null;
  }
}

// --- Per-sample panel dispatch ---

function renderPanel(sampleId, runId) {
  cancelActiveRun();
  const descriptor = samples[sampleId];
  if (!descriptor) {
    renderNotFound();
    return;
  }
  if (runId === null) {
    const wrap = el('section');
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
    renderInAppNotFound();
    return;
  }
  renderRunView(descriptor, runId, runCache);
}

function renderNotFound() {
  cancelActiveRun();
  main.innerHTML = `
    <section>
      <h1>Not found</h1>
      <p>The page or run you requested was not found.</p>
      <p><a href="/playground" data-link>Back to samples</a></p>
    </section>`;
  attachLinkHandlers(main);
}

function renderInAppNotFound() {
  renderNotFound();
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
