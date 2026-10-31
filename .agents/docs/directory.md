# Directory packages

Build a runtime folder and ZIP using only Python's standard library:

```sh
python3 scripts/pack_plugin.py --target claude --output /tmp/egm-release
python3 scripts/pack_plugin.py --target openai --output /tmp/egm-release
```

The output contains `egm-claude/` and `egm-claude.zip`, or `egm-openai/`
and `egm-openai.zip`. Existing outputs are refused. ZIP entry order,
timestamps, and permissions are fixed; identical inputs produce identical
archives on the same Python/zlib runtime.

The allowlist includes skill instructions and references, personas, standards,
runtime guides, manifests, and icons. It excludes the site, tests, workflows,
marketplace catalogs, hooks, and build tools. Claude retains its native agent
adapters; OpenAI excludes Claude components. Runtime files are nonexecutable.
Symlinked runtime paths and escaping manifest references are rejected.
Contributor instructions, the AI policy, and the Markdown PR template are
excluded. Runtime documentation links to their repository copies.
No workflow configuration ships.
The runtime guide list is explicit. New host guides do not enter these archives
until their package requirements are reviewed.

For Anthropic, the generated runtime is checked in at `distributions/claude`.
Submit that subfolder through the directory portal. After source changes,
build into a fresh temporary directory, replace `distributions/claude` with
the generated `egm-claude/` folder, and run `pytest tests/unit`. The freshness
test compares every published path and byte to a new build. Do not commit ZIPs.
For OpenAI, upload `egm-openai.zip`. Build output
alone does not establish installation, workflow support, or directory approval.
