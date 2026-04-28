import { el, renderMarkdown, renderMarkdownList, postJson } from '/playground/static/samples/_helpers.js';

export const peerreview = {
  id: 'peerreview',
  displayName: 'Peer review',
  description: {
    overview: 'A moderator coordinates a panel of specialist reviewers — technical, style, and compliance — to assess a document across multiple dimensions.',
    agents: [
      'ReviewModerator — orchestrates the panel, synthesises findings',
      'TechnicalReviewer — assesses technical correctness',
      'StyleReviewer — assesses clarity and style',
      'ComplianceReviewer — assesses regulatory / policy compliance',
    ],
    tasks: ['REVIEW → ReviewResult(document, assessment, reviewerFindings)'],
    flow: 'The ReviewModerator coordinates the three specialists, gathers their findings, and synthesises an overall assessment with per-reviewer findings called out.',
    demonstrates: 'Moderation capability with a heterogeneous panel of three specialists. Compares against research\'s delegation pattern: in delegation the coordinator decides what each specialist gets; here the moderator drives the protocol.',
  },
  agentComponentId: 'review-moderator',
  inputForm: {
    fields: [{ name: 'document', label: 'Document', type: 'textarea', rows: 10, placeholder: 'Paste the document to review…' }],
  },
  async submit({ document }, { runId } = {}) {
    const url = runId ? `/peerreview?runId=${encodeURIComponent(runId)}` : '/peerreview';
    const resp = await postJson(url, { document });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.taskId };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Review'),
      el('h4', {}, 'Assessment'),
      renderMarkdown(result?.assessment ?? ''),
      el('h4', {}, 'Reviewer findings'),
      renderMarkdownList(result?.reviewerFindings || []),
    ]);
  },
};
