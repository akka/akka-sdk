import { el, renderFields, renderMarkdown, postJson } from '/playground/static/samples/_helpers.js';

export const support = {
  id: 'support',
  displayName: 'Support',
  description: {
    overview: 'A triage agent classifies customer support requests and hands off to the appropriate specialist. The pure handoff pattern — no delegation, just routing.',
    agents: [
      'TriageAgent — classifies requests and routes to a specialist',
      'BillingSpecialist — resolves billing disputes, invoices',
      'TechnicalSpecialist — resolves technical issues, bugs',
    ],
    tasks: ['RESOLVE → SupportResolution(category, resolution, resolved)'],
    flow: 'The TriageAgent receives a RESOLVE task, analyzes the request, and hands off to the appropriate specialist. The specialist takes ownership of the same RESOLVE task, resolves the issue, and completes it.',
    demonstrates: 'Handoff capability (canHandoffTo). Sequential/relay pattern where control transfers between agents. All agents share the same task type — the task moves between agents.',
  },
  agentComponentId: 'triage-agent',
  inputForm: {
    fields: [{ name: 'issue', label: 'Issue', type: 'textarea', rows: 6, placeholder: 'My invoice for last month is wrong.' }],
  },
  async submit({ issue }, { runId } = {}) {
    const url = runId ? `/support?runId=${encodeURIComponent(runId)}` : '/support';
    const resp = await postJson(url, { issue });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.id };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Resolution'),
      renderFields([
        ['Category', result?.category],
        ['Resolved', String(result?.resolved ?? '')],
      ]),
      el('h4', {}, 'Details'),
      renderMarkdown(result?.resolution ?? ''),
    ]);
  },
};
