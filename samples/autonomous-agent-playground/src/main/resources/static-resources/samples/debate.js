import { el, renderMarkdown, renderMarkdownList, postJson } from '/playground/static/samples/_helpers.js';

export const debate = {
  id: 'debate',
  displayName: 'Debate',
  description: {
    overview: 'A moderator orchestrates a structured debate between an advocate and a critic across multiple rounds, then synthesises a balanced conclusion.',
    agents: [
      'DebateModerator — orchestrates rounds and synthesises the conclusion',
      'Advocate — argues in favor of the position',
      'Critic — argues against / surfaces weaknesses',
    ],
    tasks: ['DEBATE → DebateResult(topic, synthesis, keyArguments)'],
    flow: 'The moderator runs up to 5 moderated rounds, alternating turns between Advocate and Critic. Once rounds complete, the moderator synthesises the topic and key arguments raised by each side.',
    demonstrates: 'Moderation capability — a moderator agent shepherds a fixed pool of participants through structured rounds. Distinct from team self-coordination and from delegation.',
  },
  agentComponentId: 'debate-moderator',
  inputForm: {
    fields: [{ name: 'topic', label: 'Debate topic', type: 'text', placeholder: 'Is microservices architecture obsolete?' }],
  },
  async submit({ topic }, { runId } = {}) {
    const url = runId ? `/debate?runId=${encodeURIComponent(runId)}` : '/debate';
    const resp = await postJson(url, { topic });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.taskId };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, result?.topic ?? 'Debate'),
      el('h4', {}, 'Synthesis'),
      renderMarkdown(result?.synthesis ?? ''),
      el('h4', {}, 'Key arguments'),
      renderMarkdownList(result?.keyArguments || []),
    ]);
  },
};
