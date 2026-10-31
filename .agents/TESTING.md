# EGM Testing Playbook

This document provides a step-by-step developer playbook for running unit tests across the EGM skill suite.

---

## Setup

### A. Python Environment Setup
Create and activate an isolated Python 3.10+ virtual environment:

```bash
# Navigate to the repository root
cd /path/to/elephant-goldfish-model

# Check Python version (3.10 or newer)
python3 --version

# Create virtual environment
python3 -m venv .venv

# Activate environment
source .venv/bin/activate

# Install testing dependencies
pip install -r requirements-dev.txt
```

If your Python version is older than 3.10, install a newer version from [python.org/downloads](https://www.python.org/downloads/) or your system's package manager. Open a new terminal and verify the updated version before continuing. If `python3` still points to the older version, use the newer interpreter explicitly (for example, `python3.12 -m venv .venv`).

If you previously created `.venv` using an older Python version, delete it and recreate it using the updated version.

With Python 3.9 or older, `pytest` stops during test collection with `TypeError: unsupported operand type(s) for |`.

---

## Unit Tests

Unit tests run offline without network access, API keys, or model
dependencies. They validate YAML frontmatter schemas and relative link
integrity, the portable plugin manifest, shared metadata consistency, and
marketplace/component paths.

The read-hook tests also require a POSIX `sh` on `PATH`. On Windows, use
Git for Windows and run the tests from Git Bash, or add its `bin` directory
(containing `sh.exe`) to `PATH` in the current PowerShell session. Create
the environment with `python -m venv .venv`, then activate it with
`.venv\Scripts\Activate.ps1` in PowerShell or `source .venv/Scripts/activate`
in Git Bash. Install the test dependencies as described above.
Shell scripts use LF line endings through `.gitattributes`, including when
Git's `core.autocrlf` is enabled. CI runs this offline suite on Ubuntu and
Windows; this does not establish native host workflow support.

```bash
# Run all unit tests
pytest tests/unit

# Run with verbose output
pytest tests/unit -v

# Run a specific unit test module
pytest tests/unit/test_frontmatter.py -v
pytest tests/unit/test_link_integrity.py -v
```
---

## Native Claude Reviewer Probe (Opt-in)

With an authenticated Claude Code 2.1.271+ installation, run:

```bash
EGM_CLAUDE_LIVE=1 pytest tests/integration/test_claude.py
```

This uses the account's model quota. It builds the Claude runtime and gives it
a unique test name. Two probes invoke the review skill with folder-scoped read
permissions and no approval hook. They check that a native reviewer reads only
its persona, sees no parent or project `CLAUDE.md` context, and stops without a
verdict when an explicit deny rule blocks its persona. This is a focused probe,
not the full review workflow or a marketplace installation test.
See [Claude integration](docs/claude.md).
---

## Native Codex Integration Tests (Opt-in)

Opt-in integration tests run the live candidate through a temporary Codex marketplace:

```sh
EGM_CODEX_LIVE=1 pytest tests/integration/test_codex.py -v
```

Tests verify candidate installation, fresh-context reviewer isolation, and workflow artifacts.
Each test runs with an authenticated Codex CLI in an isolated sandbox.
See [Codex integration](docs/codex.md).

---

## Harness Test Record

A harness counts as supported after `/egm-brainstorm`, `/egm-draft`, and
`/egm-review` run end to end through its plugin install (see
[Harnesses](docs/harnesses.md)). Add a row for each run.

| Harness | Version | Date | Install | Brainstorm | Draft | Review | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Antigravity CLI | 1.4.5 | 2026-10-22 | Pass; validator warnings | Pass (fixture) | Pass (fixture) | Pass (fixture) | Temporary HOME with no hooks, MCP servers, or other plugins. The isolation probe passed on the native child transcript. 16 native reviewers ran. Stages 5 to 9 ran in sequence by timestamp. All required reads occurred. Five stage reports exist. No code was written. The final design differs from the draft in one `Status` line. Not tested: interactive approvals, `egm-bootstrap`, and synthesizer nesting. The bootstrap skill is larger than the load limit. |
| Claude Code | 2.1.308 | 2026-10-22 | Pass (`--plugin-dir`) | Pass (fixture) | Pass (fixture) | Pass after fix (fixture) | Isolation probe passed. Initial stage-order failure retained; focused nine-reviewer rerun verifies sequential stages and required reads. Process shutdown and parent no-shell fixture limits remain in the full report. |
| Claude Cowork | Opus 5.5 Medium, connected cloud task | 2026-10-22 | Pass (ZIP) | Completed with direct-dispatch recovery | Completed | Partial: stages 5–7 | Usage limit interrupted stage 8. Final artifacts, stage 9 and actual isolation-child output remain unverified; #105. Earlier complete run is reported separately. |
| OpenAI Codex | 0.163.0 | 2026-10-22 | Pass | Pass (fixture) | Pass (fixture) | Pass (fixture) | Two live tests passed in 573.13s; isolation, 15 native reviewers, ordered stages and required reads verified. |
| ChatGPT Work | GPT-6.1 Sol, Light | 2026-10-22 | Pass; installed hashes verified | Completed | Completed | Nine native reviewers completed | Fresh probe returned UNKNOWN. Artifact download/final audit and full child tool traces remain outstanding; #106. |
| omp | v18.12.6 | 2026-10-27 | Pass (community install) | Not run | Not run | Not run | Community verification, not a fixture run: all seven skills discover and resolve via `skill://` in fresh headless sessions; all nine reviewer personas register as native `task` agents. TUI `/skill:` autocomplete for hidden skills unverified; end-to-end fixtures not run. Required manifest fixes: hyphenated skill directory names (underscore dirs are rejected by omp's validator) and no `$schema` key in `plugin.json` (omp's strict agent-plugins validation rejects `disable-model-invocation`); see #186. |

### Interpreting native test results

Keep full run reports in the [PR description](https://github.com/google/elephant-goldfish-model/pull/103).
This table is a summary. Record the tested commit, client version, commands,
native evidence, and limitations in each report. Parse JSONL on literal LF;
Python `splitlines()` also splits valid Unicode characters inside JSON strings.

Track open cloud verification gaps in [#105 (Cowork)](https://github.com/google/elephant-goldfish-model/issues/105) and [#106 (ChatGPT Work)](https://github.com/google/elephant-goldfish-model/issues/106).

- Inspect native tool traces for reviewer launches, completion, and intended resource reads.
- Verify sequential stage execution and persona lenses in addition to artifact existence.
- Distinguish fixture design findings from execution failures of the review workflow.
- Keep installation, execution, and transcript isolation results recorded separately.

### Offline evidence checks

Run `pytest tests/unit/test_claude_traces.py` to exercise the Claude probe's
native child validator with synthetic records. A persona read request alone is
not a successful read: require a matching, non-error, nonempty native tool
result containing non-whitespace text, either as a string or in native text
blocks. Missing or unrelated results fail even when the child says `UNKNOWN`.
These tests do not invoke models or establish cloud support. A successful tool
result also does not by itself prove that the entire resource was read.

For Cowork and ChatGPT Work, retain separate evidence for every reviewer:
native launch arguments, canonical persona/target/standard paths, actual read
results and coverage, completion, and stage timestamps. Child-written receipts
and parent summaries cannot substitute for unavailable tool records. Inspect
the actual isolation child's answer; test host-added context separately from
parent-turn isolation. Keep raw account-bearing records private. Publish only
sanitized findings and candidate identifiers. If the surface cannot expose the
required evidence, record that boundary and leave the affected checks open.
