import { el, renderMarkdown, postJson } from '/playground/static/samples/_helpers.js';

export const dynamic = {
  id: 'dynamic',
  displayName: 'Dynamic',
  description: {
    overview: 'A single generic agent class is configured per request with different goals and task capabilities. The same DynamicAgent code runs both the summarize and translate flows.',
    agents: ['DynamicAgent — declared with no static goal or capabilities; configured at runtime via AgentSetup'],
    tasks: ['SUMMARIZE → String', 'TRANSLATE → String'],
    flow: 'Two HTTP routes (/dynamic/summarize, /dynamic/translate) each create a fresh DynamicAgent instance, configure its goal and accepted capability dynamically, then assign a single task. Same agent class, two different runtime specialisations.',
    demonstrates: 'Runtime agent configuration via AgentSetup. Useful when many task variants share the same execution shape and the differences are best expressed as data.',
  },
  agentComponentId: 'dynamic-agent',
  inputForm: {
    fields: [
      {
        name: 'mode',
        label: 'Mode',
        type: 'select',
        options: [
          { value: 'summarize', label: 'Summarize' },
          { value: 'translate', label: 'Translate to French' },
        ],
      },
      { name: 'content', label: 'Content', type: 'textarea', rows: 10, placeholder: 'Paste content to process…' },
    ],
  },
  async submit({ mode, content }, { runId } = {}) {
    const url = runId
      ? `/dynamic/${mode}?runId=${encodeURIComponent(runId)}`
      : `/dynamic/${mode}`;
    const resp = await postJson(url, { content });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.taskId };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Output'),
      renderMarkdown(String(result ?? '')),
    ]);
  },
};
