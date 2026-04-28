import { el, renderFields, renderMarkdown, postJson, getJson } from '/playground/static/samples/_helpers.js';

export const publishing = {
  id: 'publishing',
  displayName: 'Publishing',
  description: {
    overview: 'A 3-task pipeline drafts a blog post, gates on human approval, and publishes — wired together with task dependencies and an unassigned task that a human completes through the API.',
    agents: [
      'ContentAgent — drafts a blog post on the requested topic',
      'PublishingAgent — publishes an approved post (assigns URL and timestamp)',
    ],
    tasks: [
      'DRAFT → DraftPost (produced by ContentAgent)',
      'APPROVAL → ApprovalDecision (unassigned; depends on DRAFT; completed by a human)',
      'PUBLISH → PublishedPost (depends on APPROVAL; produced by PublishingAgent)',
    ],
    flow: 'POST creates all three tasks up front. Once DRAFT is ready, you read it inline and either approve or reject. Approval drives PUBLISH; rejection cancels the chain.',
    demonstrates: 'Task dependencies as orchestration. Human-in-the-loop gating without a coordinator agent. Unassigned tasks completed via HTTP.',
  },
  agentComponentId: 'content-agent',
  inputForm: {
    fields: [{ name: 'topic', label: 'Topic', type: 'text', placeholder: 'The future of edge AI' }],
  },
  async submit({ topic }, { runId } = {}) {
    const url = runId ? `/publishing?runId=${encodeURIComponent(runId)}` : '/publishing';
    const resp = await postJson(url, { topic });
    return {
      runId: resp.runId,
      agentComponentId: resp.agentComponentId,
      taskId: resp.publishTaskId,
      extras: {
        draftTaskId: resp.draftTaskId,
        approvalTaskId: resp.approvalTaskId,
        publishTaskId: resp.publishTaskId,
      },
    };
  },
  // Overlay AWAITING_INPUT detection: poll the DRAFT task; if completed and PUBLISH still PENDING/RUNNING,
  // we are waiting for the operator to approve/reject. Once the operator has acted (approvalAcked
  // flag set by the action button below), stop overriding so the server's runState (RUNNING →
  // COMPLETED once the publish task finishes) takes over.
  async overrideRunState(serverState, _statusJson, runCache) {
    if (serverState === 'COMPLETED' || serverState === 'FAILED' || serverState === 'CANCELLED') return null;
    if (runCache.extras?.approvalAcked) return null;
    try {
      const draft = await getJson(`/publishing/draft/${runCache.extras.draftTaskId}`);
      if (draft.status === 'COMPLETED') return 'AWAITING_INPUT';
    } catch (e) {
      // Ignore — keep server state.
    }
    return null;
  },
  renderResult(result) {
    return el('div', { className: 'result' }, [
      el('h3', {}, 'Published'),
      renderFields([
        ['URL', result?.url],
        ['Published at', result?.publishedAt],
      ]),
    ]);
  },
  renderActions(state, _statusJson, _runId, runCache, options = {}) {
    if (state !== 'AWAITING_INPUT') return null;
    const wrap = el('div', { className: 'awaiting-input' });
    const draftBlock = el('div', { className: 'draft-preview' }, [el('p', { className: 'muted' }, 'Loading draft…')]);
    wrap.appendChild(el('h3', {}, 'Awaiting your approval'));
    wrap.appendChild(draftBlock);

    // Fetch and render the draft body inline (FR-013 artifact display). The body is almost
    // always Markdown — render it as HTML so the operator can read it as intended.
    getJson(`/publishing/draft/${runCache.extras.draftTaskId}`).then((d) => {
      const draft = d?.result;
      if (!draft) return;
      draftBlock.innerHTML = '';
      draftBlock.appendChild(el('h4', {}, draft.title ?? 'Draft'));
      const bodyWrap = el('div', { className: 'draft-body' });
      bodyWrap.appendChild(renderMarkdown(draft.body ?? draft.content ?? ''));
      draftBlock.appendChild(bodyWrap);
    }).catch(() => {});

    const buttons = el('div', { className: 'action-buttons' });

    // Optimistic collapse: hide the AWAITING block instantly on click, before the network
    // round-trip. This is critical because the 5s backstop poll can fire during the await,
    // re-render a fresh AWAITING wrap (detaching the one our closure has), and leave the user
    // staring at a full AWAITING UI until the post-POST status fetch finally clears actions.
    // Setting approvalAcked first short-circuits overrideRunState during the race window.
    function collapse(label) {
      runCache.extras.approvalAcked = true;
      wrap.replaceChildren(el('p', { className: 'muted' }, label));
    }

    function showError(message) {
      // POST failed — undo the optimistic ack so the next poll re-renders AWAITING_INPUT,
      // and surface the error inline. The user can retry by waiting for the next poll.
      runCache.extras.approvalAcked = false;
      wrap.replaceChildren(el('p', { role: 'alert' }, message));
    }

    const approveBtn = el('button', {
      type: 'button',
      onClick: async () => {
        collapse('Approved — waiting for publish to complete…');
        try {
          await postJson(`/publishing/approve/${runCache.extras.approvalTaskId}`, {
            approvedBy: 'operator',
            comment: 'Approved via UI',
          });
          options.refreshNow?.();
        } catch (err) {
          showError(`Approve failed: ${err.message ?? err}`);
        }
      },
    }, 'Approve');

    const rejectBtn = el('button', {
      type: 'button',
      onClick: async () => {
        const reason = prompt('Reason for rejection?', 'Not ready');
        if (reason == null) return;
        collapse('Rejected — pipeline cancelled.');
        try {
          await postJson(`/publishing/reject/${runCache.extras.approvalTaskId}`, {
            rejectedBy: 'operator',
            reason,
          });
          options.refreshNow?.();
        } catch (err) {
          showError(`Reject failed: ${err.message ?? err}`);
        }
      },
    }, 'Reject');

    buttons.appendChild(approveBtn);
    buttons.appendChild(rejectBtn);
    wrap.appendChild(buttons);
    return wrap;
  },
};
