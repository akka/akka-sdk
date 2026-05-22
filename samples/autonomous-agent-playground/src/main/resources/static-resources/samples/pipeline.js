import { el, renderMarkdown, postJson } from '/playground/static/samples/_helpers.js';

export const pipeline = {
  id: 'pipeline',
  displayName: 'Pipeline',
  description: {
    overview: 'A single agent processes three tasks in a dependency chain: collect data, analyze it, then write a report.',
    agents: ['ReportAgent — handles all three pipeline tasks in sequence'],
    tasks: [
      'COLLECT → ReportResult(phase, content)',
      'ANALYZE → ReportResult(phase, content) — depends on COLLECT',
      'REPORT → ReportResult(phase, content) — depends on ANALYZE',
    ],
    flow: 'The endpoint creates all three tasks up front with dependency relationships, then assigns them to a single ReportAgent. The agent processes them in dependency order — ANALYZE waits for COLLECT, REPORT waits for ANALYZE.',
    demonstrates: 'Task dependencies as an ordering mechanism. Multiple tasks assigned to a single agent. Sequential pipeline without multi-agent coordination — ordering comes from task dependencies, not from handoff.',
  },
  agentComponentId: 'report-agent',
  inputForm: {
    fields: [{ name: 'topic', label: 'Topic', type: 'text', placeholder: 'Akka adoption in fintech' }],
  },
  async submit({ topic }, { runId } = {}) {
    const url = runId ? `/pipeline?runId=${encodeURIComponent(runId)}` : '/pipeline';
    const resp = await postJson(url, { topic });
    // Track all three tasks in extras; status polling tracks the last (REPORT) task.
    return {
      runId: resp.runId,
      agentComponentId: resp.agentComponentId,
      taskId: resp.reportTaskId,
      extras: {
        collectTaskId: resp.collectTaskId,
        analyzeTaskId: resp.analyzeTaskId,
        reportTaskId: resp.reportTaskId,
      },
    };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, `Phase: ${result.phase ?? ''}`),
      renderMarkdown(result?.content ?? ''),
    ]);
  },
};
