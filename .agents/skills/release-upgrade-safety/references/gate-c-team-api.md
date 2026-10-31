# Gate C — Team API wire — `packages/contracts/src/team-protocol/`

Triggered by any change under that directory.

Let git match the frozen set. A protocol released since you last read this file is frozen too, so
nothing here names a version:

```bash
git diff --stat --diff-filter=MDR <tag>..HEAD -- \
  'packages/contracts/src/team-protocol/v*.ts' \
  'packages/contracts/src/team-protocol/fixtures/**'
```

**Empty is the clean answer, but do not treat non-empty as rare.** The release audited when this
skill was written modified all four adapters and `v1.ts`, legitimately: renaming the product concept
forced the encode and decode halves apart, because they had shared one implementation only for as
long as the wire vocabulary and the app vocabulary were the same words. Expect to read hunks. What
the emptiness of this command actually buys you is that nothing frozen changed *without* you
noticing — so a non-empty result is the start of the work, and an empty one is only trustworthy if
you have proved the command can still speak.

Three things in that command are load-bearing:

- **Every pathspec is quoted, so git expands it and the shell never sees a glob.** Do not build the
  list into a variable: an unquoted expansion splits in bash and not in zsh, and the check then
  prints empty for the wrong reason. This gate's entire signal is its own emptiness, so before
  trusting a clean result, run it once over a range you know modifies a frozen file.
- **`v*.ts` covers the adapters.** `packages/contracts/AGENTS.md` freezes a codec, an adapter, and
  client and host fixtures for every released protocol, so `v1-adapter.ts`, `v2-adapter.ts`,
  `v3-adapter.ts` and `v3-webrtc-adapter.ts` are as frozen as `v1.ts`. A pattern anchored on
  `v[0-9]+\.ts$` matches one frozen file in five and passes while four broken adapters ship.
- **`--diff-filter=MDR`.** A released file *modified, deleted or renamed* is a broken contract with
  every peer still running an older build, and the fix is a new protocol version, never an edit.
  `R` is listed deliberately: git classifies a rename as `R` rather than `M`, so leaving it out
  lets a frozen fixture be renamed out from under the check while the diff still reports clean.
  `A` is left out because an added fixture is additive.

Read the additions separately with `--diff-filter=A` and confirm each is a genuinely new case
rather than the other half of a rename.

If the modified-or-deleted diff is non-empty, read every hunk before calling it a stop. A change
that provably cannot alter what any encoded payload means — an added `export` keyword, a comment —
is not a wire change. Anything that touches a key list, a validator, or a projected value is.

- Every shipped protocol is still registered, and the negotiation maximum only ever grows. Age and
  SemVer distance are not reasons to drop an adapter (`packages/contracts/AGENTS.md`).
- A new additive capability belongs in `current.ts`, never in a frozen codec.
- **Any new key needs a `current-agent-keys.ts` decision.** The failure is silent, not loud: the
  frozen codecs project through a key allowlist, an unmapped key is *dropped*, the encode then
  fails validation and returns `null`, and callers read `null` as "nothing to send". `tsc` stays
  green throughout.

No test reads the frozen fixtures. Do this check by hand. List the fixture folders of both
revisions, for the same reason the diff is derived rather than typed:

```bash
{ git ls-tree --name-only <tag> packages/contracts/src/team-protocol/fixtures/
  git ls-tree --name-only HEAD packages/contracts/src/team-protocol/fixtures/; } | sort -u
```

Union both revisions, as gate B does. The tag alone would skip a protocol the release *adds*, and
the one codec with no shipped history is the one this audit has the least other evidence about.
`HEAD` alone would hide a frozen fixture folder the release *deleted*. For each frozen codec or
adapter hunk from the diff above, read the fixtures of its version. Make sure that the hunk does
not change how the codec encodes or decodes those payloads.

The full compatibility matrix — older client against new host, new client against older host,
matching versions, no shared protocol, capability omission, unknown optional events, malformed
known events — stays where it is, in `docs/RELEASING.md` preflight item 0. Point at it; do not
restate it.
