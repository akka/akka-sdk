import { el, renderMarkdown, renderMarkdownList, postJson } from '/playground/static/samples/_helpers.js';

export const editorial = {
  id: 'editorial',
  displayName: 'Editorial',
  description: {
    overview: 'An editor-in-chief coordinates a research editor, a writing lead, and a review moderator by delegating a stage task to each. Each section lead is itself a coordinator: the research editor delegates to researchers, the writing lead leads a writing team, and the review moderator runs a moderated review panel.',
    agents: [
      'EditorInChief — delegates research, writing, and review stages; synthesises the final article',
      'ResearchEditor — delegates to two reporters on different angles, returns a digest',
      'Reporter (×2) — investigates an angle, saves findings to the shared workspace',
      'WritingLead — leads a writing team to produce the draft',
      'SectionWriter, CopyEditor — writing-team members',
      'ReviewEditor — moderates a review panel, returns review notes',
      'AccuracyReviewer, ReadabilityReviewer — review-panel participants',
    ],
    tasks: [
      'ARTICLE → Article(title, body, keyPoints)',
      'RESEARCH → ResearchDigest(summary, documentIds)',
      'FINDINGS → ResearchFindings(angle, summary, documentId)',
      'DRAFT → ArticleDraft(title, body, documentIds)',
      'SECTION → SectionDraft(sectionTitle, summary, documentId)',
      'REVIEW → ReviewReport(assessment, notes)',
    ],
    flow: 'The EditorInChief receives an ARTICLE task and delegates a stage task to each section lead in turn — research, then writing, then review — passing each result into the next. Each lead does its own inner coordination (delegation, team leadership, or nested moderation) to fulfil its task. The editor-in-chief synthesises the final article from the returned results. The model decides the order and whether to revisit a stage.',
    demonstrates: 'Capability mixing across a hierarchy: top-level delegation, delegation within research, team leadership within writing, and nested moderation within review. Delegation gives each stage its own held task, so a lead\'s team or panel runs without burning the parent\'s budget. Shared document workspace (DocumentTools) for bulky artifacts, with typed task results carrying structure.',
  },
  agentComponentId: 'editor-in-chief',
  inputForm: {
    fields: [{ name: 'topic', label: 'Article topic', type: 'textarea', rows: 4, placeholder: 'How autonomous AI agents coordinate in multi-agent systems' }],
  },
  async submit({ topic }, { runId } = {}) {
    const url = runId ? `/editorial?runId=${encodeURIComponent(runId)}` : '/editorial';
    const resp = await postJson(url, { topic });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.taskId };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, result?.title ?? 'Article'),
      el('h4', {}, 'Body'),
      renderMarkdown(result?.body ?? ''),
      el('h4', {}, 'Key points'),
      renderMarkdownList(result?.keyPoints || []),
    ]);
  },
};
