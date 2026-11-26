# AGENTS.md: Career Vault collaboration protocol

Two AI agents build this repo: **Claude** (Claude Code CLI) and **Gemini** (Antigravity `agy` CLI).
The **human is the orchestrator**: they open one CLI and type `next`. Agents never start each other.
Coordination happens ONLY on the Plane board (workspace `career-vault`, project `CV`), reached through the `plane` MCP server.

Full spec: `docs/implementation_plan.md`. Read only the sections your issue needs.

## Who am I?
- Running in Claude Code → you are **Claude**. Your worktree: `../career-vault-claude`.
- Running in agy / Antigravity → you are **Gemini**. Your worktree: `../career-vault-gemini`.
- "Other agent" = the one you are not.

## Board states
`Backlog` → `Ready for Claude` | `Ready for Gemini` → `In Progress` → `Review by Claude` | `Review by Gemini` → `Fix (Claude)` | `Fix (Gemini)` → `Done`

Labels: `size:S|M|L`, `area:*`, `needs-human`.

## The `next` command
When the human types `next` (or `next CV-<N>`):

1. Use Plane MCP to find ONE issue, in this priority order:
   `Fix (<me>)` → `Review by <me>` → `Ready for <me>` (highest priority first, then lowest ID).
   Skip issues labeled `needs-human` or with blockers that aren't Done. If you find nothing, say "nothing for me" and stop.
2. Read the issue description AND **all comments** (the other agent and the human talk to you there).
3. Do the task (see below). Take exactly **one** issue per `next`, then stop and report to the human in 3–5 lines.

### Build (from `Ready for <me>`)
1. Move the issue to `In Progress` and comment `🤖 <me> picked this up`.
2. Run `git switch main && git pull` (if a remote exists), then `git switch -c cv-<N>-<slug>` in your worktree.
3. Implement ONLY what the acceptance criteria say. Don't expand the scope.
4. Test:
   `podman run --rm -v "$PWD":/app:Z -v ~/.m2:/root/.m2:Z -w /app docker.io/library/maven:3-eclipse-temurin-25 mvn -q test`
5. Commit on the branch, with the message `CV-<N>: <summary>`.
6. Comment using the **Handoff template** below, then move the issue to `Review by <other>`.

### Review (from `Review by <me>`)
1. Run `git fetch` (if a remote exists), then `git diff main...cv-<N>-<slug>`. Read the issue's acceptance criteria.
2. Check correctness, the acceptance criteria, security (path traversal, auth on routes), and that tests pass. Run the tests yourself.
3. Comment using the **Review template**.
   - Approved: `git switch main && git merge --no-ff cv-<N>-<slug>`, move the issue to `Done`, then move any issues it was blocking whose blockers are now all Done to their `Ready for <builder>` state.
   - Changes needed: move the issue to `Fix (<author>)`.

### Fix (from `Fix (<me>)`)
1. Reply to each review point (✅ fixed / ❓ disagree + why).
2. Fix on the same branch, run the tests, commit, then move the issue to `Review by <other>`.
3. If this issue has had **3 fix rounds** already, add `needs-human` instead and stop.

## Templates
**Handoff**
```
🤖 <me> → <other> | CV-<N> ready for review
Branch: cv-<N>-<slug>  Commit: <sha>
Changed: <bullets>
Tests: <pass/fail + count>
Questions/risks: <bullets or "none">
```
**Review**
```
🔍 <me> review of CV-<N>: APPROVED | CHANGES NEEDED
1. <file:line> <problem> → <suggested fix>
...
```

## Rules
- Never work on an issue you didn't pick with `next`. Never push to `main` except through a review merge.
- Never edit the other agent's worktree. Never run the other CLI.
- Never commit `.env` or secrets.
- Keep reports short. The board is the source of truth, not the chat.
