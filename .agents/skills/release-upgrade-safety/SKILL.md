---
name: release-upgrade-safety
description: Audit a pending OpenBot release for upgrade and data-loss hazards before the version is bumped or tagged. Use when asked to cut a release, create or bump a version, prepare a tag, or check whether a change is safe to ship to installed users.
---

# Release upgrade safety

Every hazard here is one an installed user pays for and you cannot take back. `openbot.db` is
migrated in place with no backup, two dozen files under `userData` are rewritten by whichever build
opens them last, a released Team API adapter is spoken by peers you will never update, and the
account Worker's D1 migrations are applied before the Worker that needs them. A build that ships
past one of these does not fail on your machine; it fails on someone else's, once, permanently.

Audit the diff since the last released tag against the eight gates below, then report a verdict.
This runs *before* `docs/RELEASING.md`, which stays authoritative for the publish itself.

## What this skill does and does not do

- It audits. It does not run `bun run release:patch`, commit a version bump, or push a tag unless
  you are separately asked to.
- It never runs `bun run check`, `check:desktop`, `test`, or `build-storybook` — each takes minutes,
  CI owns them, and the desktop suite flakes under load, so a red result would tell you nothing.
  Run the narrowest test file named by a gate and lint the changed files. Do not run the repo-wide
  `bun run lint` or `bun run typecheck`: the pre-commit hook and CI run them.
- **If gate C or D fired, confirm the mobile typecheck passed.** `apps/mobile` depends on
  `@openbot/contracts`, which is exactly what those two gates change. The pre-commit hook runs
  `typecheck:mobile` when a commit stages a file that mobile can see, such as one in
  `packages/contracts`, and the CI Surfaces job runs it on each pull request.
- It never runs `bun run dev:seed` or `dev:reset` — both destroy the developer's own profile — and
  never `pkill -f`, which kills other sessions' work mid-write.

## Step 1 — establish the range, then account for every file in it

```bash
git status --porcelain
git tag --list 'v*' --sort=-v:refname | head -1
git diff --name-status <tag>..HEAD
```

**Stop if the tree is not clean.** The audit is over the commit range, not the working tree, and
that is only sound if the working tree is empty — otherwise the range you audit and the range that
ships are different sets. Nothing upstream of you enforces this: `scripts/prepare-release.ts` writes
the version bump without looking at `git status`, and its closing instruction is "Review, commit,
push, run preflight, then tag it", so following it sweeps whatever was uncommitted into the release.
`scripts/release-preflight.ts` does check `git status --porcelain`, but it runs *after* that commit,
by which point the tree is clean and the unaudited work is inside the tag. Commit or stash first.

State the tag and the changed-file count up front, and run every gate's `git diff` over that same
range. A hazard three commits back is in scope; the file you have open is not, unless it is
committed.

**The outcome Step 1 owes Step 3: every changed file is either routed to a gate, or dismissed for a
reason you can state.** A release here runs to hundreds of files — the range audited when this skill
was written was 942 — so group them however makes the residue small, and record in the Step 3 table
how each group was cleared. The failure this skill exists to prevent is almost never a hazard that
was examined and misjudged. It is one that was never looked at, because nothing pointed at it. If
the residue you cannot account for is large, that is itself the finding: report the count rather
than writing a dismissal you did not earn.

**The diff drives the audit, not the trigger lists.** Each gate names the paths that obviously
belong to it. Treat those as a starting point and never as the authority: they are written by hand,
they go stale, and a release can touch a file nobody thought to list. The question a gate actually
asks is "does this file carry the hazard I own", and only the file in front of you answers that.

Four things look dismissible and are not. Note what the last three have in common: **nothing this
audit delegates a check to can be dismissed by the bucket its file lives in.**

- **`src/renderer/**`** — the renderer writes `localStorage`, that store lives in the user's
  Electron partition, and it outlives the build that wrote it exactly the way a file under
  `userData` does. Route it through gate B.
- **The tests a gate relies on.** "Not in the shipped app" is true of every test and irrelevant for
  these, because a gate that delegates its check to a test inherits that test's weakening.
  `openbot-database-schema-parity.test.ts` is the whole mechanical half of gate A's DDL rule;
  `openbot-database.test.ts` carries the downgrade guard; `ipc-channel-coverage.test.ts` is gate D's
  only static link between main and preload. Gates C and F have no test; they use a manual check.
  A change that makes one of those assertions vacuous passes every later gate, and once it is tagged the weakening is behind the
  range every future audit starts from. Route a modification or deletion of one through the gate
  that names it and read the hunks. Other `**/*.test.ts` files dismiss normally.
- **`docs/RELEASING.md`**, for the same reason one level up. This audit does not restate the Team
  API compatibility matrix — gate C points at preflight item 0 and stops there — and it hands the
  publish itself back to that document at Step 3. Weakening either is invisible to every gate here,
  because the gate's own text still reads correctly while the thing it defers to no longer says what
  it assumed. Read its changed hunks under gate C and the handoff.
- **A directory cleared by a derived query**, such as gate B's `git grep` over `src/main` and
  `src/backend`. It inherits that gate's obligation: if the query returns nothing, the pattern is
  broken, not the directory clean.

The rest of `docs/**`, `tools/**` and `src/renderer/stories/**` are not in the shipped app at all,
which is a reason that covers every file in them. "Nothing looked interesting" is not.

## Step 2 — the eight gates

Work through all eight. A gate no path triggered is reported as **not triggered** — never dropped
silently, because "I did not look" and "I looked and it was clean" are different verdicts and only
one of them is a release gate.

Load a gate's reference when its trigger fires, not before. The trigger sets are complete here, so
a gate can be selected without opening anything:

- **A. SQLite schema** → [gate-a-sqlite.md](references/gate-a-sqlite.md)
  `src/backend/openbot-database-schema.ts`, `src/backend/database/`.
- **B. On-disk state outside SQLite** → [gate-b-on-disk-state.md](references/gate-b-on-disk-state.md)
  `src/main/index.ts`, `src/backend/workspace-paths.ts`, `src/main/sunshine-moonlight-runtime.ts`,
  `electron-builder.yml`, **any file that decodes a versioned payload**, and **any file that reads
  or writes a client-side store or its keys** — `localStorage` under `src/renderer/src/**`,
  `SecureStore` and `AsyncStorage` under `apps/mobile/src/**`, and `packages/team-client/src/**`.
  A store owner does not have to decode a version to belong here: `trusted-host-keys.ts` writes raw
  public keys, and renaming its key strands them. The reference derives both sets with `git grep`;
  do not trust a hand-written list of files.
- **C. Team API wire** → [gate-c-team-api.md](references/gate-c-team-api.md)
  anything under `packages/contracts/src/team-protocol/`, plus `docs/RELEASING.md`, which this
  audit defers its compatibility matrix to.
- **D. IPC channels** → [gate-d-ipc.md](references/gate-d-ipc.md)
  `packages/contracts/src/ipc-endpoints.ts` **or any of its mirrors** — `src/main/index.ts`,
  `src/main/ipc/`, `src/preload/index.ts`, `src/renderer/src/preview/mock-openbot.ts`. Deleting a
  handler or an `invoke` breaks a live channel without touching the list at all.
- **E. Account Worker** → [gate-e-account-worker.md](references/gate-e-account-worker.md)
  any change under `apps/auth-api/migrations/`, any non-UI file under `apps/auth-api/src/`, and the
  four files that decide deploy order: `.github/workflows/ci.yml`, `scripts/deploy-auth-api.ts`,
  `apps/auth-api/package.json`, `apps/auth-api/wrangler.jsonc`.
- **F. The updater itself** → [gate-f-updater.md](references/gate-f-updater.md)
  `electron-builder.yml`, `src/main/update-service.ts`, `package.json` (including its dependencies),
  and the two files that enforce the gate at release time: `scripts/verify-update-artifacts.ts` and
  `.github/workflows/release.yml`. Weakening a size limit or a manifest check lives only in those
  two, so omitting them makes the weakening read as "not triggered".
- **G. Reverse states and the changelog** → [gate-g-reverse-states.md](references/gate-g-reverse-states.md)
  always triggered; no path exempts a release.
- **H. Signal** — no reference file.
  always triggered: an earlier release can have left Signal behind, so a range with no `remote/`
  change does not prove that Signal is current. Signal is not deployed by CI or by the release
  workflow. Read `commit` from `curl -fsS https://signal.openbot.run/health/live`; if it is missing,
  `unknown`, or not a commit in this repository, the verdict is **needs a human**. Pass when
  `git diff --quiet <running commit> HEAD -- remote packages/contracts/src/signal-protocol`
  succeeds. Otherwise the verdict is **deploy Signal from the new tag** before the tag is pushed
  (`docs/RELEASING.md` preflight item 16); this is not a stop for the version bump. #1661 is the
  failure this catches: v0.33.0 clients sent webhook routes to a Signal older than #1520.

`references/surfaces.md` holds the exhaustive path inventory. Load it when a gate fires and you
need the exact file, not before.

**Some checks in those gates pass by producing no output. Prove the command can still speak before
you trust its silence** — run it over a range you know contains a hit, or confirm a list you expect
to be long is not empty. An empty result means "nothing is wrong" and "I asked the wrong question"
equally well, and the second is the more common of the two. This is not hypothetical: it is how
`check:ui` lost two checks (`AGENTS.md`, Tests) and why the repo deleted `no-runtime-typeof`.

## Step 2b — the pinned runtimes

Not a gate: nothing here can strand a user's data, and a stale pin is not a stop. It belongs to
this audit because this is the one moment per release when somebody looks at the whole range, and a
pinned runtime only reaches users through a release.

`native-runtime.lock.json` pins the provider CLIs and Bun, the runtime a STDIO MCP server is
started with. Bun and OpenCode have pin scripts; the rest are pinned by hand:

```bash
bun run pin:bun-runtime       # prints the block; says "already pins Bun <version>" when it matches
bun run pin:opencode-runtime
```

Ask two questions and report the answers with the table:

- **Has the pinned version a published security fix?** If so, moving the pin is part of this
  release, not the next one. A user cannot update Bun themselves: OpenBot downloaded it, OpenBot
  owns it.
- **Did the range move a pin?** Then read the diff as a dependency change. A moved `assetSha256`
  with an unmoved `version` is a stop and needs a human: the registry does not rewrite a published
  artifact.

Moving a pin needs the version assertion on a matching host, which `pin:bun-runtime` performs only
for the target it runs on. [docs/RELEASING.md](../../../docs/RELEASING.md) holds the procedure.

## Step 3 — report and hand off

Report a table:

| Gate | Triggered by | Verdict |
| --- | --- | --- |
| A. SQLite schema | *path, or "not triggered"* | pass / stop / needs a human |

Then state the stops explicitly. Any one of these means **the version is not cuttable yet**:

- a frozen Team API codec, adapter or fixture that was deleted, renamed, or modified **in a way
  that can change what an encoded payload means** — the narrow exception gate C allows, a hunk that
  provably cannot alter the wire, is not a stop, and gate C is where that call gets made;
- a DDL migration not mirrored into `LATEST_SCHEMA_SQL`;
- a renamed on-disk file, or a bumped stored `version`, with no read path for the old one;
- a D1 contraction without the two-step release;
- a changed `appId`, `ElectronTeamID`, or `publish` target.

If every gate passes, say so and hand off: `docs/RELEASING.md` owns the publish — the preflight
checklist, the compatibility matrix, signing, notarization, the canary update and the size gates.
