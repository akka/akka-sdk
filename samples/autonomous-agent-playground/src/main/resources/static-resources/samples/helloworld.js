import { el, renderFields, postJson } from '/playground/static/samples/_helpers.js';

export const helloworld = {
  id: 'helloworld',
  displayName: 'Hello world',
  description: {
    overview: 'The simplest autonomous agent sample. A single agent answers a question and returns a typed result.',
    agents: ['QuestionAnswerer — answers a question with a confidence score'],
    tasks: ['ANSWER → Answer(answer, confidence)'],
    flow: 'A user submits a question. The endpoint creates a QuestionAnswerer instance and runs a single ANSWER task. The agent processes the question and returns a structured answer with a confidence score.',
    demonstrates: 'Basic autonomous agent lifecycle — task creation, agent execution, typed result retrieval. No coordination, no tools, no multi-agent interaction. The minimum viable autonomous agent.',
  },
  agentComponentId: 'question-answerer',
  inputForm: {
    fields: [
      { name: 'question', label: 'Question', type: 'text', placeholder: 'What is the capital of France?' },
    ],
  },
  async submit({ question }, { runId } = {}) {
    const url = runId ? `/questions?runId=${encodeURIComponent(runId)}` : '/questions';
    const resp = await postJson(url, { question });
    return { runId: resp.runId, agentComponentId: resp.agentComponentId, taskId: resp.id };
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Answer'),
      renderFields([
        ['Answer', result.answer],
        ['Confidence', result.confidence],
      ]),
    ]);
  },
};
