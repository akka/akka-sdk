// SSE client + event-row rendering for the run panel. US2 (FR-009/010/011/012).

import { el, escapeHtml } from '/playground/static/samples/_helpers.js';

/**
 * Tracks observed agent instances per role within a run; once a role has > 1 instance, all rows
 * for that role are re-labelled with a short suffix so they can be told apart (FR-010a). Also
 * tracks first-seen order so {@link #hueFor} can assign each agent a well-spaced color.
 */
export class AgentDisplay {
  constructor() {
    this.instancesByComponentId = new Map(); // componentId -> Set<instanceId>
    this.orderByComponentId = new Map(); // componentId -> insertion index
  }

  observe(componentId, instanceId) {
    if (!componentId) return false;
    if (!this.orderByComponentId.has(componentId)) {
      this.orderByComponentId.set(componentId, this.orderByComponentId.size);
    }
    let bucket = this.instancesByComponentId.get(componentId);
    if (!bucket) {
      bucket = new Set();
      this.instancesByComponentId.set(componentId, bucket);
    }
    const before = bucket.size;
    if (instanceId) bucket.add(instanceId);
    // Returns true when this is the moment we crossed 1 → 2 (caller can re-label existing rows).
    return before === 1 && bucket.size === 2;
  }

  labelFor(componentId, instanceId) {
    if (!componentId) return '';
    const bucket = this.instancesByComponentId.get(componentId);
    if (!bucket || bucket.size <= 1 || !instanceId) return componentId;
    return `${componentId} ${instanceId.slice(0, 6)}`;
  }

  hueFor(componentId) {
    if (!componentId) return null;
    const idx = this.orderByComponentId.get(componentId);
    if (idx == null) return null;
    return AGENT_HUES[idx % AGENT_HUES.length];
  }
}

/**
 * Returns the (componentId, instanceId) of the agent whose stream emitted this event. The
 * server-side merger tags every envelope with its emitter, so this is now a direct read.
 */
function extractAgent(envelope) {
  return {
    componentId: envelope.agentComponentId ?? null,
    instanceId: envelope.agentInstanceId ?? null,
  };
}

// Hue palette for agent component-id pills, ordered so adjacent slots are far apart on the color
// wheel. Agents are assigned hues by first-seen order (see AgentDisplay.hueFor), so the first few
// agents in a run always get the best-separated colors regardless of their names.
const AGENT_HUES = [210, 330, 90, 270, 30, 180, 60, 150];

function summaryFor(envelope) {
  const r = envelope.raw ?? {};
  switch (envelope.kind) {
    case 'Activated': return 'Agent activated';
    case 'Deactivated': return 'Agent deactivated';
    case 'IterationStarted': return 'Iteration started';
    case 'IterationCompleted': return 'Iteration completed';
    case 'IterationFailed': return `Iteration failed: ${r.reason ?? ''}`;
    case 'Paused': return `Paused (${r.reason ?? ''})`;
    case 'Resumed': return `Resumed (${r.reason ?? ''})`;
    case 'Stopped': return `Stopped (${r.reason ?? ''})`;
    case 'TaskAssigned': return `Task assigned: ${r.taskId ?? ''}`;
    case 'TaskStarted': return `Task started: ${r.taskName ?? r.taskId ?? ''}`;
    case 'TaskCompleted': return `Task completed: ${r.taskName ?? r.taskId ?? ''}`;
    case 'TaskFailed': return `Task failed: ${r.taskName ?? ''} — ${r.reason ?? ''}`;
    case 'TaskCancelled': return `Task cancelled: ${r.taskName ?? ''} — ${r.reason ?? ''}`;
    case 'TaskResultRejected': return `Result rejected: ${r.taskName ?? ''} — ${r.reason ?? ''}`;
    case 'TaskDependencyWait':
      return `Waiting for dependencies: ${(r.pendingDependencyTaskIds ?? []).join(', ')}`;
    case 'DependencyResolved':
      return `Dependency ${r.success ? 'resolved' : 'failed'}: ${r.dependencyTaskId ?? ''}`;
    case 'HandoffStarted': return `Handed off → ${r.targetComponentId ?? ''}`;
    case 'HandoffReceived': return `Received handoff ← ${r.sourceComponentId ?? ''}`;
    case 'DelegationStarted':
      return `Delegated ${r.delegationCount ?? ''} subtask(s) to ${(r.workerComponentIds ?? []).join(', ')}`;
    case 'DelegationResolved':
      return `Delegation resolved: ${r.succeeded ?? 0} succeeded, ${r.failed ?? 0} failed`;
    case 'WorkerTaskReceived': return `Subtask received from ${r.orchestratorComponentId ?? ''}`;
    case 'WorkerTaskCompleted': return `Subtask completed`;
    case 'TeamCreated': return `Team formed: ${(r.memberComponentIds ?? []).join(', ')}`;
    case 'TeamMemberReady': return `Team member ready: ${r.memberComponentId ?? ''}`;
    case 'TeamMemberSetupFailed':
      return `Team member setup failed: ${r.memberComponentId ?? ''} — ${r.reason ?? ''}`;
    case 'TeamMemberStopped': return `Team member stopped: ${r.memberComponentId ?? ''}`;
    case 'TeamDisbanded': return `Team disbanded`;
    case 'TeamJoined': return `Joined team led by ${r.leadComponentId ?? ''}`;
    default:
      return envelope.kind;
  }
}

// Format a Date as 24-hour HH:mm:ss in the local timezone (locale-independent — avoids AM/PM).
function formatHms(d) {
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

const TIER_LABEL = {
  healthy: 'OK',
  struggle: 'Struggle',
  terminal_failure: 'Failure',
};

const TIER_GLYPH = {
  healthy: '✓',
  struggle: '⚠',
  terminal_failure: '✕',
};

export function renderEventRow(envelope, agentDisplay) {
  const { componentId, instanceId } = extractAgent(envelope);
  if (componentId) agentDisplay.observe(componentId, instanceId);
  const label = agentDisplay.labelFor(componentId, instanceId);

  const time = formatHms(new Date(envelope.timestamp));
  const tier = envelope.tier ?? 'healthy';

  const row = el('li', {
    className: 'event-row',
    'data-tier': tier,
    'data-category': envelope.category ?? 'other',
  });
  const header = el('div', { className: 'event-row-head' });
  header.appendChild(el('span', { className: 'event-tier', 'aria-label': TIER_LABEL[tier] }, TIER_GLYPH[tier] ?? '·'));
  header.appendChild(el('time', { className: 'event-time' }, time));
  if (label) {
    const attrs = { className: 'event-agent', title: instanceId ?? '' };
    const hue = agentDisplay.hueFor(componentId);
    if (hue != null) attrs.style = `--agent-hue: ${hue}`;
    header.appendChild(el('span', attrs, label));
  }
  header.appendChild(el('span', { className: 'event-kind' }, envelope.kind));
  row.appendChild(header);

  const summary = summaryFor(envelope);
  if (summary) row.appendChild(el('p', { className: 'event-summary' }, summary));

  // Per-iteration token annotation (FR-012a).
  if (envelope.kind === 'IterationCompleted') {
    const usage = envelope.raw?.tokenUsage;
    if (usage) {
      row.appendChild(
        el('p', { className: 'event-tokens muted' },
          `${usage.input ?? usage.inputTokens ?? 0} in / ${usage.output ?? usage.outputTokens ?? 0} out`)
      );
    }
  }

  return row;
}

/**
 * Opens an EventSource for the given run and pushes envelopes through onEnvelope. Returns a
 * cancel function. The browser handles reconnect automatically; the caller should react to
 * onError to update the connection-status indicator and refetch /status on recovery.
 */
export function connectEventStream(runId, agentComponentId, callbacks) {
  const url = `/playground/api/runs/${encodeURIComponent(runId)}/events?component=${encodeURIComponent(agentComponentId)}`;
  const es = new EventSource(url);
  es.onmessage = (e) => {
    // Empty data frames are SSE keep-alives — the SDK sends `data:` lines as heartbeats every
    // ~10s while the stream is idle. Silently ignore; only a non-empty payload is news.
    if (!e.data) return;
    try {
      const envelope = JSON.parse(e.data);
      callbacks.onEnvelope?.(envelope);
    } catch (err) {
      // Genuine parse failure (malformed payload) — surface to the console for debugging.
      console.warn('[playground] SSE parse failed', err, e.data);
    }
  };
  es.onopen = () => callbacks.onOpen?.();
  es.onerror = () => callbacks.onError?.();
  return () => es.close();
}
