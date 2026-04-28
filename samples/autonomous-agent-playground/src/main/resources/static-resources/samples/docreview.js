import { el, renderFields, renderMarkdown, renderMarkdownList, postJson } from '/playground/static/samples/_helpers.js';

export const docreview = {
  id: 'docreview',
  displayName: 'Document review',
  description: {
    overview: 'A single agent reviews a document for compliance, receiving the document content as a task attachment rather than inline in the instructions.',
    agents: ['DocumentReviewer — reviews an attached document against the given instructions'],
    tasks: ['REVIEW → ReviewResult(assessment, findings, compliant)'],
    flow: 'A user submits a document body and review instructions. The endpoint creates a REVIEW task with the instructions as task instructions and the document as a TextMessageContent attachment. The agent reviews the document and produces a compliance assessment.',
    demonstrates: 'Task attachments for passing large content to agents without embedding it in instruction text. Structured result types with multiple fields.',
  },
  agentComponentId: 'document-reviewer',
  inputForm: {
    fields: [
      { name: 'document', label: 'Document body', type: 'textarea', rows: 10, placeholder: 'Paste the document here…' },
      { name: 'reviewInstructions', label: 'Review instructions', type: 'text', placeholder: 'Flag any GDPR concerns.' },
    ],
  },
  async submit({ document, reviewInstructions }, { runId } = {}) {
    const url = runId ? `/docreview?runId=${encodeURIComponent(runId)}` : '/docreview';
    const resp = await postJson(url, { document, reviewInstructions });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.id };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Review'),
      renderFields([['Compliant', String(result.compliant ?? '')]]),
      el('h4', {}, 'Assessment'),
      renderMarkdown(result.assessment ?? ''),
      el('h4', {}, 'Findings'),
      renderMarkdownList(result.findings || []),
    ]);
  },
};
