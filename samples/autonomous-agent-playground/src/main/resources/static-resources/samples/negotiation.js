import { el, renderFields, renderMarkdown, postJson } from '/playground/static/samples/_helpers.js';

export const negotiation = {
  id: 'negotiation',
  displayName: 'Negotiation',
  description: {
    overview: 'A facilitator coordinates a multi-round negotiation between a buyer and a seller until they converge on terms.',
    agents: [
      'Facilitator — directs the negotiation, decides when to stop, declares the outcome',
      'Buyer — negotiates from the buyer\'s perspective',
      'Seller — negotiates from the seller\'s perspective',
    ],
    tasks: ['NEGOTIATE → NegotiationResult(topic, outcome, finalOffer)'],
    flow: 'The Facilitator runs up to 10 moderated rounds of offers and counteroffers. Each party reads prior offers and responds. The Facilitator stops when terms converge or the round limit hits.',
    demonstrates: 'Moderation capability with two adversarial participants. Same structural pattern as debate, applied to converging negotiation.',
  },
  agentComponentId: 'facilitator',
  inputForm: {
    fields: [{ name: 'topic', label: 'Negotiation topic', type: 'text', placeholder: 'Acquiring a 5-year-old SaaS startup at $20M' }],
  },
  async submit({ topic }, { runId } = {}) {
    const url = runId ? `/negotiation?runId=${encodeURIComponent(runId)}` : '/negotiation';
    const resp = await postJson(url, { topic });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.taskId };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, result?.topic ?? 'Negotiation'),
      renderFields([['Final offer', result?.finalOffer]]),
      el('h4', {}, 'Outcome'),
      renderMarkdown(result?.outcome ?? ''),
    ]);
  },
};
