import { el, renderFields, postJson } from '/playground/static/samples/_helpers.js';

export const consulting = {
  id: 'consulting',
  displayName: 'Consulting',
  description: {
    overview: 'A coordinator that can both delegate routine research to a subordinate and hand off complex problems to a senior specialist.',
    agents: [
      'ConsultingCoordinator — assesses the problem, routes to appropriate expertise',
      'ConsultingResearcher — investigates specific aspects (delegation target)',
      'SeniorConsultant — handles complex, high-stakes issues (handoff target)',
    ],
    tasks: [
      'ENGAGEMENT → ConsultingResult(assessment, recommendation, escalated)',
      'RESEARCH → ResearchSummary(topic, findings)',
    ],
    flow: 'The coordinator assesses complexity. For standard problems, it delegates a RESEARCH task and synthesises a recommendation. For complex problems (regulatory, M&A), it hands off the entire engagement to the SeniorConsultant.',
    demonstrates: 'Composing delegation and handoff in a single agent. Delegation creates a child task; handoff transfers ownership of the current task.',
  },
  inputForm: {
    fields: [{ name: 'problem', label: 'Client problem', type: 'textarea', rows: 6, placeholder: 'We are considering an M&A; assess regulatory risk.' }],
  },
  async submit({ problem }) {
    const resp = await postJson('/consulting', { problem });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.id };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Engagement result'),
      renderFields([
        ['Assessment', result?.assessment],
        ['Recommendation', result?.recommendation],
        ['Escalated', String(result?.escalated ?? '')],
      ]),
    ]);
  },
};
