// Per-run cumulative token + iteration counter (FR-012b). Updated live as IterationCompleted
// envelopes flow in.

import { el } from '/playground/static/samples/_helpers.js';

export class RunSummary {
  constructor(rootElement) {
    this.root = rootElement;
    this.iterations = 0;
    this.inputTokens = 0;
    this.outputTokens = 0;
    this.render();
  }

  ingest(envelope) {
    if (envelope.kind !== 'IterationCompleted') return;
    this.iterations += 1;
    const usage = envelope.raw?.tokenUsage;
    if (usage) {
      this.inputTokens += Number(usage.input ?? usage.inputTokens ?? 0);
      this.outputTokens += Number(usage.output ?? usage.outputTokens ?? 0);
    }
    this.render();
  }

  render() {
    this.root.replaceChildren(
      el('span', { className: 'summary-pill' }, `${this.iterations} iteration${this.iterations === 1 ? '' : 's'}`),
      el('span', { className: 'summary-pill' }, `${this.inputTokens} in tokens`),
      el('span', { className: 'summary-pill' }, `${this.outputTokens} out tokens`),
    );
  }
}
