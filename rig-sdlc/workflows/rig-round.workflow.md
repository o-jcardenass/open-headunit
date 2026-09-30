# rig-round workflow script

The branch is markdown only, so the script travels inside this file. Install it with the command in `../README.md`; the code block below is the whole script. It is the judgement half only (plan, grade, report); the host runs `rig-executor` in the foreground, per rig `CLAUDE.md` rule 4.

```js
export const meta = {
  name: 'rig-round',
  description: 'Judgement half of a rig round: plan (read the brief), grade (one run from its executor block), report (draft the results file). Never drives a device; the host runs executors in the foreground.',
  whenToUse: 'Launched by the /rig-round skill only.',
  phases: [{ title: 'Prepare' }, { title: 'Runs' }, { title: 'Report' }],
}

// Rig CLAUDE.md rule 4: no background agent drives a device. This workflow therefore never launches
// rig-executor for a run; the host does that in the foreground. The one executor use here is a
// grep-only pass over an existing capture (no device commands).
const a = args || {}
const EV = `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/evidence/${a.thread}-round${a.round}`
const ctx = `Thread ${a.thread}, round ${a.round}. Brief: ${a.brief}. Worktree: ${a.worktree}. Evidence dir: ${EV}. Scripts: /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts (source rig_devices.sh).`

const S = {
  plan: { type: 'object', properties: {
    runs: { type: 'array', items: { type: 'object', properties: {
      id: { type: 'string' }, title: { type: 'string' }, point: { type: 'boolean' }, handStep: { type: 'boolean' },
      untestable: { type: 'string' }, steps: { type: 'string' }, conditions: { type: 'string' } }, required: ['id', 'steps', 'conditions'] } },
    stopRule: { type: 'string' }, setupNotes: { type: 'array', items: { type: 'string' } },
    needsMarkers: { type: 'boolean' },
    build: { type: 'object', properties: { branch: { type: 'string' }, sha: { type: 'string' } } } }, required: ['runs'] },
  block: { type: 'object', properties: {
    run: { type: 'string' }, commands: { type: 'array', items: { type: 'string' } }, exit_codes: { type: 'array', items: { type: 'integer' } },
    markers: { type: 'object' }, greps: { type: 'array', items: { type: 'object' } }, anomalies: { type: 'array', items: { type: 'string' } } }, required: ['run', 'commands', 'greps', 'anomalies'] },
  grade: { type: 'object', properties: {
    verdict: { type: 'string', enum: ['PASS', 'FAIL', 'INCONCLUSIVE', 'UNTESTABLE'] }, reason: { type: 'string' },
    mismatch: { type: 'boolean' }, stopRound: { type: 'boolean' }, escalate: { type: 'string' } }, required: ['verdict', 'reason', 'mismatch', 'stopRound'] },
}

const mode = a.mode
if (mode === 'plan') {
  const plan = await agent(`${ctx}\nMode plan.`, { agentType: 'rig-grader', label: 'read brief', schema: S.plan })
  if (!plan) return { status: 'STOPPED', reason: 'the brief reader returned nothing' }
  return { status: 'PLANNED', plan }
}

if (mode === 'grade') {
  // a.run = {id, conditions}; a.block = the executor's JSON block, already run by the host.
  const gp = (block, second) => agent(`${ctx}\nMode grade for ${a.run.id}${second ? ', second pass. If counts still disagree with yours, grade from your own greps and say so' : ''}. Also write your grade to ${EV}/${a.run.id}.grade.json. Conditions:\n${a.run.conditions}\nExecutor block:\n${JSON.stringify(block)}`,
    { agentType: 'rig-grader', label: `grade ${a.run.id}${second ? ' (2)' : ''}`, schema: S.grade })
  let block = a.block
  let g = await gp(block, false)
  if (g && g.mismatch) {
    block = await agent(`${ctx}\nGrep pass only for ${a.run.id}, no device commands: re-run every grep in this block inside its marker window and return the corrected block.\n${JSON.stringify(block)}`,
      { agentType: 'rig-executor', label: `regrep ${a.run.id}`, schema: S.block })
    g = await gp(block, true)
  }
  if (!g) return { status: 'STOPPED', reason: `grader returned nothing for ${a.run.id}` }
  return { status: 'GRADED', id: a.run.id, verdict: g.verdict, reason: g.reason, stopRound: g.stopRound, escalate: g.escalate }
}

if (mode === 'report') {
  // a.graded = [{id, verdict, reason}], a.setupNotes = [...]; earlier launches' files are in the evidence dir.
  await agent(`${ctx}\nMode report. Verdicts: ${JSON.stringify(a.graded || [])}. Include every <run>.json and <run>.grade.json in the evidence dir. Setup notes: ${JSON.stringify(a.setupNotes || [])}. Write the draft to ${EV}/${a.thread}-round${a.round}-results.md.`,
    { agentType: 'rig-grader', label: 'draft results' })
  return { status: 'AWAITING_APPROVAL', results: `${EV}/${a.thread}-round${a.round}-results.md` }
}

return { status: 'STOPPED', reason: 'args.mode must be plan, grade or report' }
```
