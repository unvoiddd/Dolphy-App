# Gate B — On-disk state outside SQLite — every decoder of a versioned file

Triggered by a change to `src/main/index.ts`, `src/backend/workspace-paths.ts`,
`src/main/sunshine-moonlight-runtime.ts`, `electron-builder.yml`, or **any file that decodes a
versioned payload**. Derive that last set instead of trusting a list — a serialization owner is not
always a `*-store.ts`, and `central-auth-manager.ts`, `main-window-state.ts` and
`skill-marketplace-service.ts` are three that are not:

```bash
git grep -liE 'version *(!==|===) *[0-9]|version: *z\.literal' <tag> HEAD -- \
  'src/main/*.ts' 'src/backend/*.ts' ':(exclude)*.test.ts' | sed 's/^[^:]*://' | sort -u
```

The gate fires if the release diff touches any file that prints. Four deliberate details:

- **Both revisions, unioned.** Searching `HEAD` alone means *removing* a version guard removes the
  file from the result, hiding the change most worth auditing. The tag alone misses an owner the
  release adds, and the tag side carries the pre-rename filename you need to spot a rename.
- **`z.literal` is a second spelling** — `remote-desktop-secret-store.ts` pins its version by schema
  and matches no comparison operator. Widen the pattern when you meet a third spelling rather than
  trusting this one.
- **No `\b`** — `git grep -E` is POSIX ERE, where it matches nothing and the list comes back empty.
- **`-i`** — a prefixed field such as `layoutVersion !== 2` (`sidebar-layout-store.ts`) has a
  capital `V`, and a case-sensitive search skips it.

The list is still only a starting point, and it is short enough to eyeball: if it prints nothing, or
far less than the file inventory in `references/surfaces.md`, the pattern is broken rather than the
release clean. Step 1 is what actually guarantees an unmatched file gets looked at — a persistence
change spelled in a way no pattern anticipates is caught by having to dismiss the file by name.

**Three surfaces persist outside `userData` and none of them is on the lists above.** The renderer's
`localStorage` lives in the user's Electron partition; the mobile app's `SecureStore` and
`AsyncStorage` live on the phone; `packages/team-client` writes workspace preferences through a
storage interface both of them supply. All three outlive the build that wrote them exactly the way a
file under `userData` does, and the query above reaches none of them — it is scoped to `src/main`
and `src/backend`, and these keep state under a string key rather than guarding a `version` field.

Widen the first query's pathspec to include `'apps/mobile/src/*.ts'` and
`'packages/team-client/src/*.ts'` — that is what catches `workspace-preferences.ts`, which does
guard `value.version !== 1`. Then run the key inventory as a second derived list:

```bash
keys() { git grep -hoE '"openbot[.:][a-zA-Z0-9.:_-]+"' "$1" -- \
  'src/renderer/src/**' 'apps/mobile/src/**' 'packages/team-client/src/**' \
  ':(exclude)*.test.ts' ':(exclude)*.test.tsx' | sort -u; }
diff <(keys <tag>) <(keys HEAD)
```

Process substitution rather than two files in `/tmp`: several agents work in worktrees on this
machine at once, and a fixed temporary name lets one audit read another range's inventory and call
a renamed key clean. **`openbot[.:]` covers both spellings** — the renderer uses colons
(`openbot:sidebar-pins:v1`), mobile and team-client use dots (`openbot.mobile.session.v1`), and a
pattern written for one silently returns half the set. It still only sees string literals, and two
keys are built in templates and never appear at all — `workspace-preferences.ts` composes
`openbot.workspace.v1.${scope}.${fingerprint}` and `trusted-host-keys.ts` composes
`openbot.host-key.v1.${scope}.${fingerprint}`, the second holding per-host trust. Widen the pattern
when you meet a third spelling rather than trusting this one, and treat the key diff as necessary
rather than sufficient.

A key that disappears from the tag side is these surfaces' version of a renamed filename constant:
the old entry is never read again, never cleaned up, and the user's state is silently back to
defaults. Several keys carry their own version suffix — `openbot:sidebar-pins:v1`,
`openbot.mobile.session.v1`, `openbot.mobile.device-id.v1` — so bumping one is a deliberate reset
needing the same justification as a `userData` version bump, plus a `CHANGELOG.md` note under gate G.

**An unchanged key list is not a clean result — read the owners too.** The value under a key can
change shape while the key never moves, and that is the more likely half: it is a schema tightened,
an enum member renamed, an id format rewritten. Derive the owners the same way, union both
revisions, and read the hunks of any that the release touched:

```bash
git grep -lE '"openbot[.:]|localStorage|SecureStore|AsyncStorage' <tag> HEAD -- \
  'src/renderer/src/**' 'apps/mobile/src/**' 'packages/team-client/src/**' \
  ':(exclude)*.test.ts' ':(exclude)*.test.tsx' | sed 's/^[^:]*://' | sort -u
```

**No one of these three passes is sufficient, and they fail in different directions.** Check that
each of the two files with a templated key lands somewhere: `trusted-host-keys.ts` appears here
because it names `SecureStore`, and is invisible to both other passes; `workspace-preferences.ts`
appears in *neither* the key list nor this one — it takes its storage as an injected parameter and
names no API — and is reached only by the widened `version` query above. If a file you know persists
is missing from all three, the patterns are what is broken.

Expect this list to churn when files move between directories while the key list stays still — it is
the *hunks* that matter here, not the paths. `sidebar-pins.ts` is the worked example, and it is the
reason this paragraph exists: the release that renamed the product concept left
`openbot:sidebar-pins:v1` and its Zod schema untouched and added `reownSidebarPinnedItems`, which
rewrites every stored `bot-<uuid>` pin to the matching `agent-<uuid>`. All of the migration lived
inside the value. Had it been forgotten, every user's pins would point at ids the app no longer
knows and would be dropped on the next read — and the key diff above would still print nothing.

The trigger lives here rather than in `references/surfaces.md` because you classify triggers before
you open any reference; a trigger that pointed at the inventory would need the inventory read first,
and a file you did not already suspect would be classified as not triggered. Once this gate fires,
open the inventory for the file-by-file detail.

- **Did an on-disk filename constant change?** A rename is silent data loss: the old file is never
  found, never read, and never deleted. The new build starts from its defaults and the user's
  setup, servers or window state are simply gone.
- **Did a stored payload `version` bump?** Every shipped previous shape needs a read path. Three
  working precedents to copy, in descending order of care:
  - `src/main/remote-server-stored-shape.ts` — reads v1 and v2 as a re-tag of v3, preserves entries
    it cannot parse rather than dropping them, and **refuses an unknown version outright** so it
    never overwrites a file a newer build can still read.
  - `src/main/team-store.ts` — keeps `openbot-team-server-v1.json` and `-v2.json` on disk together,
    so a user who downgrades still finds their host.
  - `src/main/dynamic-island-preference-store.ts` — reads `version === 1` and `=== 2` into 3.
  - Known gap, re-check every release: `src/main/setup-store.ts` accepts only `version === 2` and
    falls back to defaults on anything else, silently. Bumping it to 3 without a read path re-runs
    setup for every installed user.
- **A read path is only the upgrade half — check the downgrade too.** Proving the new build reads
  every old shape says nothing about the old build reading the *new* one, and every file here is
  rewritten in place by whichever build opens it last. A user who rolls back to the previous
  release, or runs an older install beside the new one, hands the file to a reader that predates
  the bump. `src/main/dynamic-island-preference-store.ts` treats an unknown version as defaults and
  then rewrites the file at its own version, so shipping a `version: 4` under that filename means
  the older build silently discards the user's setting rather than leaving it alone. Before reusing
  a filename, read the reader **at the release tag** and confirm it refuses an unknown version
  instead of defaulting — `src/main/remote-server-stored-shape.ts` does, which is what makes it safe
  to bump. If it does not, keep the old file and write the new version beside it under a new name,
  the way `src/main/team-store.ts` keeps `-v1.json` and `-v2.json`.
  - The same asymmetry applies to `central-auth-manager.ts`, which is stricter still: an unknown
    shape *throws*, and `#initialize` catches that into `#clearStoredSession()`. The user is signed
    out, and because the file is `safeStorage`-encrypted there is nothing to recover by hand.
- **The permanent names are permanent.** The four legacy path prefixes in
  `src/backend/workspace-paths.ts`, `bots.json` (`LEGACY_AGENTS_STATE_FILE` in
  `src/backend/agent-store.ts`), `mailbox.json`, and the `legacy-import:bots:v1` command id are
  spellings a shipped release already wrote to the user's disk. A database restored from the user's
  own file copy never ran migration v13, so `bot-<uuid>` ids and `~/OpenBot/Bots` paths are still
  live values.
- **`legacy-backup-v1/` is not a general backup.** `DatabaseCore.backupLegacyFile` copies a single
  named file, once, with `COPYFILE_EXCL`, and it is used for the two legacy JSON imports only.
  Nothing else on disk is ever copied before being rewritten.
- **`safeStorage` files become undecryptable** if the macOS signing identity, team ID or `appId`
  changes: `openbot-central-auth-v1.bin` and the remote-desktop secret files are encrypted against
  the keychain entry those identify. Diff `electron-builder.yml` for `appId`, `ElectronTeamID`, and
  the `publish` block — this overlaps gate F on purpose, because one field breaks two things.
