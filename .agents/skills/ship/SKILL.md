---
name: ship
description: Ship the current branch to main. Review the diff, fix findings, open the pull request, babysit the NorbiAI review and CI until green, then squash-merge. Use when a feature is done and the user asks to ship it, open and merge a PR, or get the change to main.
---

# Ship

Move one finished branch to `main` through five gates: local review, pull
request, NorbiAI review, CI, merge. Do each gate in sequence. Do not skip a
gate to go faster.

When the user invokes this skill, they approve the merge at the end, but only
when every gate is green. Stop and ask when a gate needs a product decision,
a force-push, or a bypass.

## Before you start

- Read `AGENTS.md` and each nested `AGENTS.md` for a directory in the diff.
- Run `git status` and `git branch --show-current`. When the branch is
  `main`, create a branch first. Do not ship from `main`.
- Run `git fetch origin main`. Get the change set with
  `git diff --stat origin/main...HEAD` and `git status --short`.
- When a pull request for the branch exists (`gh pr view --json number,state,url`),
  continue from the first gate that is not done.
- Keep a checklist in `TASKS.md` with one item for each gate.

## Gate 1 — Review the diff locally

A local review is less expensive than a NorbiAI round. Find the problems
before the push.

1. Commit the finished work. Write the message in ASD-STE100 Simplified
   Technical English. Leave unrelated changes out of the commit.
2. Review `origin/main...HEAD` for correctness, data loss, the
   non-negotiable rules in `AGENTS.md`, and reuse. Use the `code-review`
   skill when it is available, or a review subagent with the diff range.
3. Triage each finding as `babysit` Step 2 says: fix only findings that
   you can prove. Record the others as not valid, with the reason.
4. Run the narrowest relevant test file and
   `biome check --write --max-diagnostics=none <changed paths>`. Run one
   typecheck project for the code you changed, as `AGENTS.md` says. Do not
   run broad checks.
5. Commit the fixes. The pre-commit hook runs its own checks. Do not use
   `--no-verify`. When the hook fails, fix the cause and commit again.

For a UI change, run the `smoke` skill and take before and after
screenshots. Upload them with `gh pr create --attach` or
`gh pr edit --attach` (gh 2.101 or later; run `gh --version`). Do not
commit them.

## Gate 2 — Open the pull request

1. Push the branch: `git push -u origin HEAD`.
2. Write the body from `.github/PULL_REQUEST_TEMPLATE.md`. Fill
   `## What changed` with the user-visible result and the surfaces the
   change touches. Fill `## Verification` with the checks you ran and the
   checks you left to CI. Remove the sections the template says to remove.
3. State the model and harness in the body.
4. Create the PR: `gh pr create --base main --title "<title>" --body-file <file>`.
   Write the title as one imperative sentence, as recent `main` commits do.
5. When the `link_pull_request` tool is available, link the PR URL.

## While you wait

Gates 3 to 5 wait for a long time. `main` can move during the wait and
cause a merge conflict. GitHub does not run `pull_request` workflows on a
PR with a merge conflict. Thus `Check` and `NorbiAI review` do not start,
and a wait for them does not end.

1. Before each wait, and at each poll, run
   `gh pr view <pr> --json mergeable,mergeStateStatus,headRefOid`.
   Poll at most once each 2 minutes.
2. `UNKNOWN` means that GitHub calculates the state. Query again at the
   next poll. Do not act on it.
3. `CONFLICTING` or `DIRTY` means a merge conflict. Stop the wait. Go to
   Gate 4, step 5.
4. When no `Check` or `NorbiAI review` status shows on the head SHA 10
   minutes after a push, query `mergeable` first. Then read the runs with
   `gh run list --branch <branch> --limit 5`. Do not continue the wait
   with no signal.

## Gate 3 — Babysit the NorbiAI review

Run the `babysit` skill on the PR. It owns the review loop: triage, fixes,
rebuttals, and re-review requests. Continue to Gate 4 only when the
`NorbiAI review` status on the head SHA is success. During each `babysit`
wait, do the checks in "While you wait".

## Gate 4 — Make CI green

The branch protection on `main` requires the `Check` status and resolved
conversations. It does not require the branch to be up to date with `main`:
a PR that is behind `main` can merge when it has no merge conflict.

1. Query `mergeable` first, as "While you wait" says. Then watch the
   checks on the head SHA: `gh pr checks <pr> --watch --interval 120`.
   Run it in the background, and query `mergeable` at each wake. When the
   watch shows no checks, look for a merge conflict first.
2. For each failed job, read only the failed log:
   `gh run view <run-id> --log-failed`. Find the cause.
3. When the failure comes from the change, fix it, commit, and push. Each
   push starts a new NorbiAI review. Go back to Gate 3 for that review.
4. When the failure is not related to the change, such as a network
   timeout, run `gh run rerun <run-id> --failed` once. When the same job
   fails again, stop and report it to the user.
5. Do not update the branch only because `main` moved. When
   `gh pr view <pr> --json mergeable` shows `CONFLICTING`, merge `main` into
   the branch, resolve the conflicts, and push. Do not force-push. The merge
   starts a full NorbiAI review. Go back to Gate 3. Find the conflict
   with the checks in "While you wait", not only after CI fails.
6. Answer each open human review thread. Do not resolve a thread that
   another person opened before you answer it.

Never weaken or skip a test to make CI green.

## Gate 5 — Merge

Merge only when all of these are true on the same head SHA:

- `gh pr view <pr> --json mergeStateStatus,reviewDecision,statusCheckRollup`
  shows `mergeStateStatus` `CLEAN`. `DIRTY` means a merge conflict: go back
  to Gate 4, step 5.
- `Check` and `NorbiAI review` are success.
- No `P0` or `P1` finding remains, and no conversation is open.

Then merge:

```
gh pr merge <pr> --squash --match-head-commit <head-sha>
```

`--match-head-commit` stops the merge when a new push arrived after your
last check. The repository deletes the remote branch after the merge.

Do not use `--admin`. Do not bypass branch protection. Do not delete this
worktree or the local branch.

After the merge:

1. Run `gh pr view <pr> --json state,mergeCommit` and confirm `MERGED`.
2. Run `git fetch origin main` and confirm that `origin/main` contains the
   merge commit.

## Stop and ask

Stop and ask the user when:

- A fix needs a force-push, a history rewrite, or a change outside this
  repository.
- A valid finding needs a product decision.
- A merge conflict needs a product decision: both sides change the same
  behavior with a different intent.
- The `babysit` skill stops with no progress.
- CI fails twice on a cause that you cannot fix in the change.
- The merge needs `--admin` or a protection change.

## Report format

Keep the report short:

| Gate | Result | Evidence |
| --- | --- | --- |
| Local review | findings fixed / rebutted | commit SHAs |
| Pull request | opened | PR URL |
| NorbiAI | success after N rounds | review comment link |
| CI | success | run link |
| Merge | merged | merge commit SHA on `main` |

Then list the checks you ran locally and the checks you left to CI, one line
each.
