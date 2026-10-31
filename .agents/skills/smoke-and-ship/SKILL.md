---
name: smoke-and-ship
description: Smoke test the current branch, then ship it to main only when all smoke items pass. Use when the user asks to smoke and ship, or to test a change in the real app and then merge it.
---

# Smoke and ship

Run the `smoke` skill on the current branch. When all smoke items pass, run
the `ship` skill. Do not ship a branch with a failed smoke item.

When the user invokes this skill, they approve the merge at the end, but only
when the smoke test passes and every `ship` gate is green.

Keep a checklist in `TASKS.md`: one item for the smoke test, and one item for
each `ship` gate.

## Step 1 — Smoke

Run the `smoke` skill on the current branch. Do all of its steps, including
the clean-up and the report.

## Step 2 — Decide

Use the smoke report:

- All items are `pass`: go to Step 3.
- The smoke skill says that the change has no user-visible behavior: this
  is a pass. Go to Step 3.
- One or more items are `fail`: stop. Do not ship. Give the smoke report.
  Do not fix a failure unless the user asks.
- An item is `not checked`:
  - When the cause is outside local reach (a real second computer, a paid
    provider, a production build, Windows or Linux), continue to Step 3.
    Put the item in the PR `## Verification` section.
  - For a different cause, such as a stack that did not start, stop and
    ask the user.

## Step 3 — Ship

Run the `ship` skill. Use the smoke result again:

- Do not run `smoke` again in Gate 1. For a UI change, use the screenshots
  from Step 1 when they show before and after. Take only the missing ones.
- Put the smoke table in the PR `## Verification` section.
- When a Gate 1 fix, a NorbiAI fix, or a CI fix changes user-visible
  behavior, smoke the affected items again before the push. When an item
  fails, stop as Step 2 says.

## Report format

Give the smoke table from the `smoke` skill. Then give the gate table from
the `ship` skill. When you stopped after the smoke test, give only the smoke
report and the reason that you did not ship.
