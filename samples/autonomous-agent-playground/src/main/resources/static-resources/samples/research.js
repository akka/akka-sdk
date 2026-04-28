import { el, renderMarkdown, renderMarkdownList, postJson } from '/playground/static/samples/_helpers.js';

export const research = {
  id: 'research',
  displayName: 'Research',
  description: {
    overview: 'A coordinator agent delegates research to two specialist agents, then synthesises their findings into a unified brief. The first multi-agent sample, demonstrating the delegation (fan-out/fan-in) pattern.',
    agents: [
      'ResearchCoordinator — receives the topic, delegates to specialists, synthesises results',
      'Researcher — gathers facts and sources on a topic',
      'Analyst — identifies trends and actionable insights',
    ],
    tasks: [
      'BRIEF → ResearchBrief(title, summary, keyFindings)',
      'FINDINGS → ResearchFindings(topic, facts, sources)',
      'ANALYSIS → AnalysisReport(topic, assessment, trends)',
    ],
    flow: 'The coordinator receives a BRIEF task and decides to delegate: it creates a FINDINGS task for the Researcher and an ANALYSIS task for the Analyst. Both work in isolated contexts. When both complete, results flow back to the coordinator, which synthesises a unified ResearchBrief.',
    demonstrates: 'Delegation capability (canDelegateTo). Context partitioning — each specialist sees only its slice. Fan-out to parallel workers and fan-in for synthesis.',
  },
  agentComponentId: 'research-coordinator',
  inputForm: {
    fields: [{ name: 'topic', label: 'Research topic', type: 'text', placeholder: 'Modular monoliths in 2026' }],
  },
  async submit({ topic }, { runId } = {}) {
    const url = runId ? `/research?runId=${encodeURIComponent(runId)}` : '/research';
    const resp = await postJson(url, { topic });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.id };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, result?.title ?? 'Research brief'),
      el('h4', {}, 'Summary'),
      renderMarkdown(result?.summary ?? ''),
      el('h4', {}, 'Key findings'),
      renderMarkdownList(result?.keyFindings || []),
    ]);
  },
};
