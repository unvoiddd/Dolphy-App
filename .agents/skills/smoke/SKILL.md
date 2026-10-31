---
name: smoke
description: Start one or more local OpenBot dev instances and smoke test the features that the current changes or a pull request add. Use when asked to smoke test, QA, or check a change or PR in the real app, including two-client team flows, the browser client, and the iOS simulator.
---

# Smoke

Start the local dev stack for this worktree. Then drive the real app and
prove that each feature in the change works. Report what passed, what
failed, and what you did not check.

A smoke test does not replace tests or CI. Do not run broad checks here.
`AGENTS.md` forbids them locally.

## Before you start

- Read `AGENTS.md` and the `AGENTS.md` of each directory in the change.
- Run `node --version`. Node 24 is necessary.
- Prefix each `bun run dev*` command with `env -u ELECTRON_RUN_AS_NODE`.
  An agent shell can leak this variable, and then Electron starts as Node.
- In a fresh worktree, run `bun run dev:bootstrap` once. When it reports a
  missing `.env.keys`, copy the file from the main checkout. Do not
  create a new one.

## Step 1 — Find what to test

Get the change set. Use the first source that applies:

1. A PR number or URL: `gh pr view <pr> --json title,body,headRefName,files`
   and `gh pr diff <pr> --name-only`. Check out the PR branch in this
   worktree only when the user asks. Otherwise test the current branch.
2. A branch: `git diff --name-only $(git merge-base origin/main HEAD)`,
   plus `git status --short` for uncommitted work.

`bun run dev:verify` reads only uncommitted changes. Use it for setup and
runtime state, not for the change set. Ignore the `lint` and `typecheck`
entries in its `commands`.

Read the PR body and the diff. Write a short test plan before you start
the app. Give each item a user action and an expected result:

| # | Feature | Surface | Action | Expected result |
| --- | --- | --- | --- | --- |
| 1 | Short name | desktop / test-client / web / mobile | Steps | Visible result |

Include the reverse action for each state change: snooze/unsnooze,
pause/resume, revoke/reconnect, mute/unmute. Include a restart when the
change touches persisted state or migrations.

When a change has no user-visible behavior, such as a refactor, a test,
or docs only, say so and stop. Do not start the app for it.

## Step 2 — Choose the stack

Choose the smallest stack that covers the plan:

| Plan needs | Command |
| --- | --- |
| Desktop renderer or main process | `bun run dev` |
| Two clients: teams, invitations, sharing, Team API | `bun run dev:test-client` |
| Browser client at `/app` or public web | `bun run dev:api` (add `bun run dev` when a desktop host is also necessary) |
| iOS app | `bun run dev:mobile` |

Rules:

- Run `bun run dev:status` first. Reuse this worktree's stack when it is
  live. Do not start a second stack in the same worktree.
- Never drive a stack or an app of a different worktree.
- Start the stack with `run_in_background`. Wait for the ports that the
  supervisor prints, or poll `bun run dev:status` until the service is
  live. Use the ports that the stack reports, never a fixed port.
- Do not pass `--shared`, so that the test does not change the shared
  `OpenBot Dev` profile. A new profile gets the showcase data of
  `bun run dev:seed`.
- Do not run `dev:reset`, or `dev:seed` without `--dry-run`, on a profile
  that you did not create. Ask first.

## Step 3 — Drive the app

Use `bun run dev:automation` for the desktop app and the test client:

```bash
bun run dev:automation instances
bun run dev:automation pages
bun run dev:automation snapshot --wait-for=<role>,<name>
bun run dev:automation click --role=<role> --name=<name> --allow-mutations --instance=<id> --wait-for=<role>,<name>
bun run dev:automation type --role=<role> --name=<name> --text=<text> --allow-mutations --instance=<id>
```

Follow this loop for each plan item:
`snapshot → action with --wait-for → snapshot`.

The accessibility snapshot is the evidence. Do not take screenshots by
default: they cost more. Take one only when the user asks, when the
result is visual (layout, color, animation, an image) and a snapshot
cannot show it, or when the user asks for PR evidence. Then use
`bun run dev:automation screenshot --out=<name>.png`.

- Select elements by accessible role and name. When an element has no
  accessible name, record it as a finding. Do not add a test ID.
- `--page=<target-id|url-substring>` selects a window or an embedded
  browser view. `--service=test-client` selects the second client.
- Wait for a role, not for elapsed time.
- `dev:automation` cannot close a window or run a script. For window
  lifecycle checks, use CDP on the instance's `remoteDebuggingPort`.
- For the browser client, use the preview or Chrome DevTools tools on the
  URL that the stack reports.
- For the iOS app, use the device tools to open the simulator. Take a
  simulator screenshot only when the result is visual.
- For a restart check, stop only the stack you started with
  `bun run dev:stop`, then start it again with the same flags.

Also look for problems outside the plan: renderer console errors in the
`dev:automation` stderr, errors in the stack log, and a UI that does not
respond.

A provider turn uses the signed-in subscription. Send model turns only
when the feature needs them, and keep the prompts short.

## Step 4 — Clean up

- Stop each stack you started with `bun run dev:stop`. Leave a stack that
  was live before you started.
- Never kill by process pattern. Kill only a PID that you started.
- Screenshots, if any, stay in `.openbot-build/dev-automation/`. Do not commit
  them.

## Step 5 — Report

Give one table for the plan:

| # | Feature | Result | Evidence |
| --- | --- | --- | --- |
| 1 | Short name | pass / fail / not checked | Snapshot text, log line, or screenshot path |

Then give:

- The stack and flags you used, and the surfaces you checked.
- Each failure with the steps to reproduce it, the expected result, and
  the actual result.
- Each item you did not check, and why. Examples: a real second computer,
  a paid provider, a production build, Windows or Linux.

Do not fix a failure unless the user asks. When the user asks for PR
evidence, give the screenshot paths for before and after. Do not commit
them.
