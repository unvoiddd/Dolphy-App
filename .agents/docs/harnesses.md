# Harnesses

EGM is one set of skills and personas. This repository serves as the
plugin source for each supported harness. No harness gets a fork of the skill
text.

## Install

| Harness | Install |
| --- | --- |
| Antigravity | `agy plugin install https://github.com/google/elephant-goldfish-model` (or `/plugin` in the IDE) |
| Claude Code | `claude plugin marketplace add google/elephant-goldfish-model && claude plugin install egm@egm` |
| Claude Cowork | Install from claude.ai/plugins or the Cowork plugin panel via marketplace `google/elephant-goldfish-model` |
| Codex | `codex plugin marketplace add google/elephant-goldfish-model && codex plugin add egm@egm` |
| ChatGPT | Personal testing: Plugins > Add > Upload plugin archive; see [packaging](codex.md#chatgpt). Workspace import: Admin > Plugins > Add > Import marketplace, repo URL `https://github.com/google/elephant-goldfish-model`, path `.agents/plugins` |
| omp | `omp plugin marketplace add google/elephant-goldfish-model && omp plugin install egm@egm` |

Install the whole plugin. Skill-only installers that copy `skills/` alone
break EGM, because the skills read `agents/` and
`resources/document_standards/` at run time.

## Manifests

Root `plugin.json` is the canonical source for plugin identity, version,
description, and publisher metadata. It follows the portable Agent Plugins
1.0.0 format; the `$schema` key is omitted because omp routes manifests that
declare it to a strict validator that rejects `disable-model-invocation`.
Update shared values there first, then update the compatibility
manifests; unit tests reject metadata drift. Keep platform-specific fields in
their existing manifests until installation tests justify removing them.

Marketplace files are discovery catalogs pointing to this package, not copies
of its skills. In particular, `.agents/plugins/` contains the Codex catalog,
while `agents/` contains reviewer personas. All hosts share the same `skills/`,
`agents/`, and `resources/` content.

| Harness | Manifest | Skills | Subagents |
| --- | --- | --- | --- |
| Antigravity | `plugin.json` | `skills/` (default) | `agents/` (default) |
| Claude Code | `.claude-plugin/plugin.json`, `.claude-plugin/marketplace.json` | `skills/` (default) | Claude adapters and orchestrators listed in `plugin.json` `agents` |
| Claude Cowork | `.claude-plugin/plugin.json`, `.claude-plugin/marketplace.json` | `skills/` (default) | Subagents run inside Cowork; hooks n/a |
| Codex | `.codex-plugin/plugin.json`, `.agents/plugins/marketplace.json` | `skills` path | Not shipped; general-purpose subagent reads the persona file |
| ChatGPT | `plugin.json`, `.codex-plugin/plugin.json`; `.agents/plugins/marketplace.json` for workspace import | `skills` path | Detect native tools in the current session; general-purpose agent reads the persona |
| omp | `.claude-plugin/plugin.json`, `.claude-plugin/marketplace.json` (Claude-compatible fallback) | `skills/` (default) | Personas from `agents/`/`.claude-plugin/agents/` register as native `task` agents |

Persona prompts live in `agents/`, the directory Antigravity
scans for subagents. Each persona has `name` and `description` frontmatter, so
harnesses that load `agents/*.md` register them as named subagents
(`egm-design`, `egm-zoom-out`, and so on).

When you add a reviewer persona, add a Claude adapter under
`.claude-plugin/agents/` and list it in `.claude-plugin/plugin.json`.
The adapter reads the shared persona and sets Claude's read-only tools and
context isolation. This avoids imposing Claude tool names on other hosts.
In Claude Code, `/plugin` > Errors will report that the default `agents/` folder is ignored because the manifest sets `"agents"`; this warning is expected and harmless (see [Claude integration](claude.md#claude-code-warning-default-agents-folder-is-ignored)).

## Delegation

Skills say "spawn a subagent" and name a persona. Each harness maps that
phrase to its own tool:

| Harness | Subagent tool | Named personas | Nesting |
| --- | --- | --- | --- |
| Antigravity | `invoke_subagent` | Yes | Yes |
| Claude Code | Agent (Task) tool | Yes | Up to 3 levels by default |
| Claude Cowork | Cowork subagents | Yes | Subagents run inside Cowork |
| Codex | Native spawn/wait tools; inspect the available schema | No | Detect at runtime; direct dispatch fallback |
| ChatGPT | Native spawn/wait tools when exposed by the current Work session | No | Detect at runtime; direct dispatch fallback |
| omp | `task` tool | Yes | Yes |

Where a harness has no named persona, the skill spawns a general-purpose
subagent and tells it to read `agents/<file>.md` first. If a harness cannot
nest subagents, use direct dispatch instead of the synthesizer.

A Goldfish review needs a fresh context. If a harness has no subagent tool,
the review skills stop instead of running in the same context. Check actual
tool availability rather than inferring it from the product name. This applies
to ChatGPT modes that do not expose native fresh-context delegation.

## Artifact Directory

Skills write briefs, drafts, and reports to the working directory, or to the
harness's artifact directory if it has one. In Claude Code, Claude Cowork,
Codex, and ChatGPT, you can name another folder. Add this line to your prompt,
or to an agent instructions file that your harness loads (`CLAUDE.md` or
`AGENTS.md`, in the project or at user level):

```text
EGM artifact directory: .dev/docs
```

A relative path is relative to the working directory. Design docs and other
project files do not move. Claude Code reads a project `AGENTS.md` only when
the project has no `CLAUDE.md`. To keep the files out of git, add the folder to
`.gitignore`.

## Support Status & Contributing Other Harnesses

**Antigravity**, **Claude Code**, and **Codex** pass automated installation, reviewer isolation, and end-to-end workflow fixtures.

**Claude Cowork** ([#105](https://github.com/google/elephant-goldfish-model/issues/105)) and **ChatGPT Work** ([#106](https://github.com/google/elephant-goldfish-model/issues/106)) have working installation paths with open cloud trace verification gaps.

**omp** ([#186](https://github.com/google/elephant-goldfish-model/issues/186)) has a working installation path. Skills are invoked as `/skill:egm-<command>` (omp registers one command per skill; it has no `/egm-*` namespace). Community-verified on omp v18.12.6: all seven skills discover and resolve, and all nine reviewer personas register as subagents. End-to-end workflow fixtures are not yet automated for omp.

See the [harness test record](https://github.com/google/elephant-goldfish-model/blob/main/TESTING.md#harness-test-record) for tested surfaces and limits.

We welcome contributors who want to help verify and expand harness support.

### Contributing Support for New Harnesses

A harness counts as supported after `/egm-brainstorm`, `/egm-draft`, and
`/egm-review` run end to end through its plugin install. When contributing
support for another harness:

1. Add the appropriate manifest or configuration file to the repository root
   (e.g., matching the harness's plugin/extension standard).
2. Document install steps, manifest mapping, and delegation mechanics in this guide
   (`docs/harnesses.md`).
3. Verify that `/egm-brainstorm`, `/egm-draft`, and `/egm-review` run end to end,
   and attach verification notes or execution transcripts in your Pull Request description
   (as outlined in the [Pull Request Template](https://github.com/google/elephant-goldfish-model/blob/main/.github/PULL_REQUEST_TEMPLATE.md)).
