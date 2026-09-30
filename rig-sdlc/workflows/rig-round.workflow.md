# rig-round workflow script (DRAFT)

The branch is markdown only, so the script travels inside this file. Install it with the command in `../README-DRAFT.md`; the code block below is the whole script.

```js
export const meta = {
  name: 'rig-round',
  description: 'DRAFT. Run one round brief on the rig: prepare the build, execute each run in order (Haiku, one at a time, rig lock), grade each (Sonnet), draft the results file',
  whenToUse: 'Launched by the /rig-round skill only.',
  phases: [{ title: 'Prepare' }, { title: 'Runs' }, { title: 'Report' }],
}

// No device is ever driven by two agents at once: runs are a plain for-loop, never parallel().
// Finished runs are skipped on relaunch via args.done (the evidence/<topic>-round<N>/<run>.json files).
const a = args || {}
const done = new Set(a.done || [])
const EV = `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/evidence/${a.thread}-round${a.round}`
const ctx = `Thread ${a.thread}, round ${a.round}. Brief: ${a.brief}. Worktree: ${a.worktree}. Evidence dir: ${EV}. Scripts: /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts (source rig_devices.sh).`

const S = {
  plan: { type: 'object', properties: {
    runs: { type: 'array', items: { type: 'object', properties: {
      id: { type: 'string' }, title: { type: 'string' }, point: { type: 'boolean' }, handStep: { type: 'boolean' },
      untestable: { type: 'string' }, steps: { type: 'string' }, conditions: { type: 'string' } }, required: ['id', 'steps', 'conditions'] } },
    stopRule: { type: 'string' }, setupNotes: { type: 'array', items: { type: 'string' } },
    build: { type: 'object', properties: { branch: { type: 'string' }, sha: { type: 'string' } } } }, required: ['runs'] },
  block: { type: 'object', properties: {
    run: { type: 'string' }, commands: { type: 'array', items: { type: 'string' } }, exit_codes: { type: 'array', items: { type: 'integer' } },
    markers: { type: 'object' }, greps: { type: 'array', items: { type: 'object' } }, anomalies: { type: 'array', items: { type: 'string' } } }, required: ['run', 'commands', 'greps', 'anomalies'] },
  grade: { type: 'object', properties: {
    verdict: { type: 'string', enum: ['PASS', 'FAIL', 'INCONCLUSIVE', 'UNTESTABLE'] }, reason: { type: 'string' },
    mismatch: { type: 'boolean' }, stopRound: { type: 'boolean' }, escalate: { type: 'string' } }, required: ['verdict', 'reason', 'mismatch', 'stopRound'] },
}

phase('Prepare')
const plan = await agent(`${ctx}\nMode plan.`, { agentType: 'rig-grader', label: 'read brief', schema: S.plan })
if (!plan) return { status: 'STOPPED', reason: 'the brief reader returned nothing' }
if (!done.has('prepare')) {
  const prep = await agent(`${ctx}\nPrepare step: fetch and build ${plan.build ? plan.build.branch + ' @ ' + plan.build.sha : 'the brief\'s candidate'} with HUR_DIR=<a worktree of that candidate, never this transfer worktree> build_hur.sh, then run_unit_tests.sh with the same HUR_DIR, install it per device role with adb -s <serial> install -r -d, run apk_identity.sh <apk> <serials>, back up settings.xml, and report the APK md5s and the build/test gate exit codes. Write ${EV}/prepare.json.`,
    { agentType: 'rig-executor', label: 'prepare build', schema: S.block })
  if (!prep) return { status: 'STOPPED', reason: 'prepare agent died; check the rig before relaunching', plan }
  if ((prep.exit_codes || []).some(c => c !== 0)) return { status: 'ESCALATE', reason: 'build or unit-test gate failed (section 3a)', prep }
}

phase('Runs')
const graded = []
for (const run of plan.runs) {
  if (done.has(run.id)) { log(`${run.id} already ran, skipped`); continue }
  if (run.untestable) { graded.push({ id: run.id, verdict: 'UNTESTABLE', reason: run.untestable }); continue }
  if (run.handStep && !(a.handDone || []).includes(run.id)) {
    return { status: 'AWAITING_HAND', run: run.id, reason: 'this run names a hand step; do it, then relaunch with it marked done', graded }
  }
  let block = await agent(`${ctx}\nExecute run ${run.id} exactly:\n${run.steps}\nWrite ${EV}/${run.id}.json.`,
    { agentType: 'rig-executor', label: `run ${run.id}`, schema: S.block })
  if (!block) return { status: 'STOPPED', reason: `executor died during ${run.id}; the rig may be mid-run, check it before relaunching`, graded }
  let g = await agent(`${ctx}\nMode grade for ${run.id}. Also write your grade to ${EV}/${run.id}.grade.json. Conditions:\n${run.conditions}\nExecutor block:\n${JSON.stringify(block)}`,
    { agentType: 'rig-grader', label: `grade ${run.id}`, schema: S.grade })
  if (g && g.mismatch) {
    block = await agent(`${ctx}\nGrep pass only for ${run.id}, no device commands: re-run every grep in this block inside its marker window and return the corrected block.\n${JSON.stringify(block)}`,
      { agentType: 'rig-executor', label: `regrep ${run.id}`, schema: S.block })
    g = await agent(`${ctx}\nMode grade for ${run.id}, second pass. If counts still disagree with yours, grade from your own greps and say so.\nConditions:\n${run.conditions}\nExecutor block:\n${JSON.stringify(block)}`,
      { agentType: 'rig-grader', label: `grade ${run.id} (2)`, schema: S.grade })
  }
  if (!g) return { status: 'STOPPED', reason: `grader returned nothing for ${run.id}`, graded }
  graded.push({ id: run.id, verdict: g.verdict, reason: g.reason })
  log(`${run.id}: ${g.verdict}`)
  if (g.escalate) return { status: 'ESCALATE', reason: g.escalate, run: run.id, graded }
  if (g.stopRound) { log(`stop rule met after ${run.id}`); break }
}

phase('Report')
await agent(`${ctx}\nMode report. Verdicts this launch: ${JSON.stringify(graded)}. Runs graded in earlier launches have their <run>.json and <run>.grade.json in the evidence dir; include them. Setup notes from reading the brief: ${JSON.stringify(plan.setupNotes || [])}.`,
  { agentType: 'rig-grader', label: 'draft results' })
return { status: 'AWAITING_APPROVAL', results: `${a.thread}-round${a.round}-results.md`, graded }
```
