---
name: openpanel
description: Query OpenBot product analytics (events, sessions, profiles, funnels, retention, active users, Search Console) from the self-hosted OpenPanel server, and make or change its dashboards and reports. Use when debugging with real usage data, checking whether an event fires, counting affected users, answering a product or growth question, or building a dashboard.
---

# OpenPanel analytics

`scripts/openpanel.ts` calls the OpenPanel MCP server at `https://analytics.openbot.run/api/mcp`.
The token is a `root` client. It reads analytics and makes, changes and deletes dashboards and
reports (`create_dashboard`, `create_report`, `update_report_layout`, `delete_dashboard` and others).
`apps/auth-api/.env.dev` and `apps/auth-api/.env.production` hold the same token as
`OPENPANEL_MCP_TOKEN`, so either key works.

## Run

When `DOTENV_PRIVATE_KEY_DEV` is in the shell:

```sh
bun scripts/openpanel.ts tools                       # list the tools
bun scripts/openpanel.ts tools <tool>                # show the input schema of one tool
bun scripts/openpanel.ts call <tool> '<json args>'   # call a tool; the result is JSON
```

When the key is not in the shell, use the root `.env.keys` of the main checkout. A worktree has none:

```sh
MAIN="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
bunx dotenvx run -q -f apps/auth-api/.env.dev -fk "$MAIN/.env.keys" -- bun scripts/openpanel.ts call list_event_names '{"projectId":"openbot"}'
```

If no key is available, stop and ask the developer. Never print the token, a key, or `.env.keys`.

## Use

- Every tool needs `"projectId": "openbot"`. Nightly, Infeld and other products share the
  organization, and the token can change their dashboards too: do not touch them.
- Event names and properties are in [ANALYTICS.md](../../../ANALYTICS.md). Call `list_event_names`
  or `list_event_properties` when a name is not there.
- Read the schema with `tools <tool>` before the first call of a tool. Most query tools take
  `startDate` and `endDate` (`YYYY-MM-DD`) and use the last 30 days by default.
- Before you change or delete a dashboard that you did not make in this task, ask the developer.
  Check a new report with `get_report_data` and send the developer the `dashboard_url`.
- The server accepts 60 requests per minute from one IP address. Each command is one request, so
  do not loop over many profiles.
- Profiles contain email addresses and account IDs. Do not copy them into issues, pull requests,
  commits or logs. Use counts, or an ID only when the developer asks for one.

## Rotate the token

In OpenPanel, open the OpenBot project, then Settings, Clients. Make a new `root` client. Encrypt
`base64(<client id>:<client secret>)` as `OPENPANEL_MCP_TOKEN` into both `apps/auth-api/.env.dev`
and `apps/auth-api/.env.production` with `dotenvx set ... -fk <main checkout>/.env.keys`. Then delete
the old client.
