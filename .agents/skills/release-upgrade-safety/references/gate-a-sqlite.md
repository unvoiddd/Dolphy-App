# Gate A — SQLite schema — `src/backend/openbot-database-schema.ts`

Triggered by any change to that file or to `src/backend/database/`.

- New entries in `MIGRATIONS` must **continue contiguously from the last version at the release
  tag**. Read that number, never remember it:

  ```bash
  git show "<tag>:src/backend/openbot-database-schema.ts" | grep -E 'version: [0-9]+' | tail -1
  ```

  A literal written into this file would go stale the first time a migration ships and would then
  reject the correct number. `validateMigrationRegistry` throws on a gap, so a skipped number is
  loud — but a *reused* one is a migration that never runs on any database that already applied it.

  **Quote the whole `<rev>:<path>` argument, and quote it even after you put the tag in a variable.**
  In zsh `$T:src/...` parses as the `:s` substitution modifier and dies with `bad substitution`
  before git runs — and only for paths starting `s`, so `$T:packages/...` works and teaches you the
  wrong lesson. The aborted command prints nothing, which is indistinguishable from a clean read.
  `"${T}:src/..."` is safe in both shells.
- **No shipped migration body may have changed.** Do not grep for `migrateTo` — only
  `migrateToBaselineV8` is spelled that way, so the grep comes back empty on a release that rewrote
  every other one. Derive the bodies from the registry instead:

  ```bash
  git show "<tag>:src/backend/openbot-database-schema.ts" | grep -E '^\s+up: [a-zA-Z]' | sort -u
  ```

  Read every hunk of the diff for each function it names, not the stat. A database that already
  applied version 12 will never apply it again, so an edit to it reaches new installs only and the
  two populations diverge. **One body can be `up:` for several versions** —
  `refreshProviderSessionsForDynamicTools` is currently the migration for 9, 10 and 14 — so a
  one-line edit there rewrites three shipped migrations at once, and the diff shows it as a single
  changed function far from any `version:` line.

  **The `up:` names are the entry points, not the frozen set — take the transitive closure.** A
  migration body delegates, and the helper it delegates to is as frozen as the body: at the time of
  writing `migrateToBaselineV8` is four lines of `db.prepare` followed by calls to
  `compactConversationHistory`, `compactMailboxHistory`, `migrateProviderSessionsForGrok` and
  `migrateReactionsForActors`, and the first two run `DELETE FROM orchestration_events` and
  `DELETE FROM projection_thread_activities`. Editing one of those changes what an upgrading
  database *deletes*, while new installs never execute it at all — they get `LATEST_SCHEMA_SQL` and
  the entry stamped as applied. The wrapper's own diff stays clean, so the `up:` list alone reports
  nothing. Two names against nine is the difference this makes; derive it rather than listing it:

  ```bash
  schema() { git show "${1}:src/backend/openbot-database-schema.ts"; }
  frozen() {
    src=$(schema "$1")
    defs=$(printf '%s\n' "$src" | grep -oE '^function [a-zA-Z0-9_]+' | awk '{print $2}' | sort -u)
    seen=$(printf '%s\n' "$src" | grep -oE '^[[:space:]]+up: [a-zA-Z0-9_]+' | awk '{print $2}' | sort -u)
    n=0
    while [ "$(printf '%s\n' "$seen" | wc -l)" -ne "$n" ]; do
      n=$(printf '%s\n' "$seen" | wc -l)
      for f in $(printf '%s\n' "$seen"); do
        seen="$seen
  $(printf '%s\n' "$src" | awk "/^function $f\\(/,/^}/" | grep -oE '[a-zA-Z0-9_]+\(' | tr -d '(')"
      done
      seen=$(printf '%s\n' "$seen" | sed '/^$/d' | sort -u | grep -Fx -f <(printf '%s\n' "$defs"))
    done
    printf '%s\n' "$seen"
  }
  frozen <tag>
  ```

  It iterates to a fixpoint, so a helper added one level deeper is still caught. `for f in $(...)`
  rather than `for f in $seen`: command substitution splits in both shells, bare parameter expansion
  splits only in bash. Run it against the tag, not `HEAD` — the question is what was already shipped.
- **If the migration executes DDL, `LATEST_SCHEMA_SQL` must be extended in the same change.**
  `createLatestDatabase` execs that SQL and then stamps every `MIGRATIONS` entry as applied
  *without running it*, so a DDL migration missing from it ships new installs a database without
  the column upgraded installs have. Today `LATEST_SCHEMA_SQL` is derived by `substituteOnce`,
  which replaces exactly one table in the v8 baseline (the v12 reactions table) and throws on zero
  or two matches. A second DDL migration must change that mechanism, not append to it.
- Data-preservation fixtures for every shipped source schema, plus failure, rollback, retry,
  downgrade, missing-version, foreign-key and integrity coverage, per `src/backend/AGENTS.md`.
- Confirm the downgrade guard still names the new `LATEST_SCHEMA_VERSION` — the test is
  "rejects a database created by a newer application" in `src/backend/openbot-database.test.ts`.

```bash
bun run test:desktop -- src/backend/openbot-database-schema-parity.test.ts
bun run test:desktop -- src/backend/openbot-database.test.ts
```

The parity test builds a database both ways and compares normalised `sqlite_master` and
`PRAGMA table_info`, so an unmirrored DDL migration is red rather than a support ticket. If it is
green and you added DDL, check that you actually added it to `MIGRATIONS`.
