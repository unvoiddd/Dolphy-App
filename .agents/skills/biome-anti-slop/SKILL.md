---
name: biome-anti-slop
description: Install and configure vendored Biome anti-slop GritQL rules in a local TypeScript or JavaScript repository. Use when a user asks to add, configure, update, or migrate Biome anti-slop lint rules.
---

# Biome anti-slop

Vendor the bundled rules into the current repository and integrate them with its existing Biome
setup. Preserve unrelated work and adapt to the repository's package manager and configuration.

## Install

1. Inspect the repository before changing it:
   - Read applicable agent instructions and check `git status`.
   - Detect the package manager from `packageManager` and lockfiles.
   - Find `biome.json` or `biome.jsonc` files and existing anti-slop rules.
   - If multiple independent Biome roots exist, ask which one to configure.

2. Require `@biomejs/biome` version `>=2.5.9 <3`. Preserve an existing dependency in that range. If
   Biome is absent from a package manifest, install the latest compatible 2.x release using
   `@biomejs/biome@^2.5.9` as a development dependency with the repository's package manager. If
   the repository uses a version outside that range, stop and ask before changing it.

3. From the target repository, copy the bundled rules:

   ```bash
   node <skill-directory>/scripts/install.js
   ```

   The default destination is `tools/biome/anti-slop`. Pass another relative destination as the
   first argument when the repository has an established tooling layout. The installer refuses to
   replace an existing destination; use `--force` only after reviewing the existing files and the
   replacement diff.

4. Register the installed rules. **Do not type the rule names from memory or from this file.** The
   installer prints two lists, read from the `// scope:` line each rule declares, so they cannot go
   stale as rules are added, merged, or removed:

   - the `global` list goes in the top-level `plugins` array;
   - the `tests` list goes in a `plugins` array on an `overrides` entry matching `**/*.test.ts` and
     `**/*.test.tsx`, because those patterns are legitimate in product code and only wrong in a test.

   Add them without removing existing plugins.

   Preserve every existing configuration field. Also enable these native Biome rules at error
   severity, merging them into their existing groups:

   ```json
   {
     "linter": {
       "rules": {
         "complexity": {
           "noBannedTypes": "error",
           "noUselessTypeConstraint": "error"
         },
         "nursery": {
           "noMisleadingReturnType": "error",
           "noUnsafeTypeAssertion": "error",
           "useReduceTypeParameter": "error"
         },
         "style": {
           "noNonNullAssertion": "error",
           "useAsConstAssertion": "error"
         },
         "suspicious": {
           "noExplicitAny": "error",
           "noFocusedTests": "error",
           "noSkippedTests": "warn"
         }
       }
     }
   }
   ```

   `noFocusedTests` is an error because a stray `it.only` silently disables the rest of its file;
   `noSkippedTests` is a warning because a skip has honest uses and only needs a comment naming what
   unblocks it. Confirm every rule name and its current group with the installed Biome binary before
   editing configuration. The groups above are correct for Biome 2.5.9; if `biome explain` reports
   that a later compatible 2.x release promoted a nursery rule, use its reported stable group. Stop
   if any rule is unavailable rather than writing an invalid configuration.

   If one of these rules already uses object configuration, preserve its options and change only
   its `level` to `error`. If `linter.enabled` is explicitly `false`, stop and ask before enabling
   it. Add the vendored anti-slop directory to
   `files.includes` as a negated pattern so routine checks do not reformat vendored rules. Also add
   negated patterns for project-local agent tooling directories that actually exist, such as
   `.agents` or `.codex`; do not ignore all dot-directories. If no Biome configuration exists,
   create a minimal one with the local schema, the plugin list, and those file exclusions.

5. Keep each rule's declared severity. The set runs at two levels and the difference is a promise:
   `error` is for a pattern with no honest counter-example, `warn` for a judgement a pattern cannot
   make - an options-object parameter is a readability call, whether a `Map` in a store is always
   replaced whole is a fact about the component, and whether a module has an injectable seam is a
   fact about the code under test. A warning is a prompt to think, never a demand to rewrite.

   A judgement the pattern cannot make *at all* is a different thing, and does not belong in the
   set: a rule that fires mostly on correct code trains readers to skim every warning beside it.
   Neither does a rule Biome already ships - `noUnsafeTypeAssertion` and `noExplicitAny` own their
   patterns here, and a second diagnostic on the same line adds nothing to read.

6. Every rule ships a fixture beside it under `fixtures/`, marking each line the rule must reject
   with a trailing `// flag` and surrounding it with correct code the rule must leave alone. A
   GritQL pattern that matches nothing is green and enforces nothing, so a rule without a fixture
   proving both halves is not a rule. If the target repository adds one, it adds a fixture too.

7. Run the repository's existing lint or check command. If none exists, run Biome directly. Report
   findings in owned source and fix them only when the user requested migration or cleanup. Do not
   suppress diagnostics, weaken their severity, or launder types to make the check pass.

8. Review the final diff and report the copied path, dependency change, configuration change,
   checks run, and any remaining findings.
