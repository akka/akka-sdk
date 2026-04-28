import { el, renderMarkdown, postJson } from '/playground/static/samples/_helpers.js';

export const devteam = {
  id: 'devteam',
  displayName: 'Devteam',
  description: {
    overview: 'A team lead decomposes a software project into tasks. Developer agents claim tasks, work independently, and message peers when coordination is needed.',
    agents: [
      'ProjectLead — decomposes the project, oversees the team',
      'Developer (×N) — claims tasks from a shared list, implements features, coordinates with peers',
    ],
    tasks: ['PLAN → ProjectResult', 'IMPLEMENT (per developer task)'],
    flow: 'The ProjectLead receives a PLAN task and decomposes it into developer tasks on a shared list. Developers autonomously claim and complete work, messaging peers for coordination on shared interfaces. The lead disbands the team when done.',
    demonstrates: 'Team capability with self-coordination. Shared task list where members autonomously claim and complete work. Peer messaging for coordination on shared dependencies.',
  },
  agentComponentId: 'project-lead',
  inputForm: {
    fields: [{ name: 'description', label: 'Project description', type: 'textarea', rows: 6, placeholder: 'Build a CLI to convert CSV to JSON, with tests.' }],
  },
  async submit({ description }, { runId } = {}) {
    const url = runId ? `/devteam?runId=${encodeURIComponent(runId)}` : '/devteam';
    const resp = await postJson(url, { description });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.taskId };
  },
  renderResult(result) {
    // ProjectResult is a record — render scalar fields as a fields block, long-form fields as
    // markdown so multi-paragraph LLM output stays readable.
    if (typeof result === 'string') {
      return el('div', { className: 'result' }, [el('h3', {}, 'Result'), renderMarkdown(result)]);
    }
    if (result && typeof result === 'object') {
      const wrap = el('div', { className: 'result' }, [el('h3', {}, 'Project result')]);
      for (const [k, v] of Object.entries(result)) {
        const valueStr = typeof v === 'string' ? v : JSON.stringify(v, null, 2);
        wrap.appendChild(el('h4', {}, k));
        wrap.appendChild(renderMarkdown(valueStr));
      }
      return wrap;
    }
    return el('div', { className: 'result' }, [el('p', {}, 'No result.')]);
  },
};
