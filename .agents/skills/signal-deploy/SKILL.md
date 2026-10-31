---
name: signal-deploy
description: Check and deploy Signal (the `remote-api` service in `remote/`) to production from a release tag. Use when asked to deploy, redeploy, update or roll back Signal, to check that a Signal deployment has all its secrets and no regressions, or when a release tag changes `remote/` or `packages/contracts/src/signal-protocol`.
---

# Signal deploy

Signal runs on `sui-alexandria` in the Compose project `openbot-remote` (`/opt/openbot/remote`). It
has no database: a restart closes the signalling WebSockets and empties the in-memory rooms, routes
and resume-token cache. Active WebRTC data channels stay up. coturn is a separate container.

[docs/remote-session-deployment.md](../../../docs/remote-session-deployment.md) is the source for
the staging, build and rollback commands. This skill adds the checks before and after them.

## When to deploy

CI and the release workflow do not deploy Signal. For each release, find the running commit (step 1).
When `git diff --quiet <running> <tag> -- remote packages/contracts/src/signal-protocol` finds changes,
deploy Signal from the local tag before the tag is pushed. Compare with the running commit, not with
the previous tag: an earlier release can have left Signal behind. The push starts the release
workflow, which does not wait for Signal ([docs/RELEASING.md](../../../docs/RELEASING.md), preflight
item 16). Example: #1661. The v0.33.0 clients sent `webhookRoute`, but production Signal
was older than #1520. It had no `/v1/webhooks` route and refused webhook-only ingress sockets with
`authentication_required`.

## Rules

- Deploy only a release tag, from a detached worktree: `git worktree add --detach <dir> <tag>`. Do not
  check out the tag in a checkout that another agent uses.
- Deploy Signal only. Do not run `remote:update` or `remote:up`: they also recreate coturn and end
  relay sessions. Change coturn only when the developer asks.
- Never print, copy off the host, or log an environment value, `.env.keys`, a container environment,
  a token, a ticket or raw connection logs. Compare secrets by key name, or by a hash on the host.
- The deploy reads the host files `/opt/openbot/remote/.env.production` and `.env.keys` through
  symlinks, not the `remote/.env.production` of the tag. The two files can differ.
- Get explicit approval from the developer before each production change: the deploy, a change to the
  host environment file, and a change to the host Nginx configuration.

## 1. Find the running version

First read `commit` from the public health route:

```sh
curl -fsS https://signal.openbot.run/health/live
```

If `commit` is missing (a build from before this field) or `unknown` (built without
`OPENBOT_SOURCE_COMMIT`), find the commit by checksum:

```sh
ssh sui-alexandria 'docker inspect openbot-remote-remote-api-1 --format "{{.Image}} {{.Created}} {{.State.Health.Status}}"'
ssh sui-alexandria 'docker exec openbot-remote-remote-api-1 sh -c "cd /app/remote/api/src && sha256sum *.ts"'
ssh sui-alexandria 'ls -t /opt/openbot/releases'
```

Each release directory has the full commit SHA as its name. Compare the container checksums with
`git show "${commit}:remote/api/src/<file>" | shasum -a 256` for the newest release directories and
tags. In zsh, write `"${commit}:remote/..."`: `$commit:r` is a modifier. Record the running commit.

## 2. Check for regressions

Diff the running commit against the tag:

```sh
git diff --stat <running> <tag> -- remote packages/contracts/src/signal-protocol
git merge-base --is-ancestor <running> <tag>
```

If the running commit is not an ancestor of the tag, stop and report: the deploy would remove
changes that are live now.

- **Protocol.** In `packages/contracts/src/signal-protocol`, released messages and auth events must
  stay compatible. New message types and optional fields are safe; a removed or renamed field is not.
  A new `hello` field or a new public route that released clients use needs this Signal before those
  clients ship.
- **Account service.** A new Signal call to the Worker (for example `/v2/remote/<x>-route/...`) must
  exist in the deployed Worker. An unauthenticated `POST https://api.openbot.run/<path>` gives 401
  when the route exists and 404 when it does not.
- **`remote/compose.yaml`.** A new variable with `:?` stops the start when the host file lacks it.
- **`remote/api/Dockerfile`.** A new workspace package must be copied before `bun install`.
- **`remote/nginx/signal.openbot.run.conf`.** The host uses its own copy at
  `/etc/nginx/conf.d/openbot-remote.conf`. A new `location` in the repository needs the same change on
  the host, after a backup, `nginx -t` and `systemctl reload nginx`. Without it, the request goes
  through `location /`, which has the default 1 MB body limit and writes the path to the access log.
- **Checks.** In the tag worktree: `bun install --frozen-lockfile`, then
  `bun run --cwd remote/api check`. It runs the typecheck. `remote/api` has no tests.

## 3. Check the secrets

1. Compare key names. Required keys are those that `remote/compose.yaml` marks with `:?`; optional
   keys turn a feature off when they are empty (Slack, Discord, Telegram).

   ```sh
   git show <tag>:remote/.env.production | grep -vE '^#|^$' | cut -d= -f1 | sort
   ssh sui-alexandria "grep -vE '^#|^$' /opt/openbot/remote/.env.production | cut -d= -f1 | sort"
   ```

2. Make sure that `DOTENV_PUBLIC_KEY_PRODUCTION` is the same in both files. Then the host
   `.env.keys` decrypts the encrypted values of the tag file.
3. For keys in both files, compare values by hash on the host: copy the encrypted tag file to a
   `umask 077` temporary file, run `bin/dotenvx get -f <file> -fk .env.keys` for each file, and print
   only `<key> same|DIFFERENT|only-in-tag|only-on-host`. Delete the temporary files.
4. Report each key that is only in the tag or that is different. The developer decides which value
   goes live. To add a key, copy its encrypted line from the tag file into the host file after
   `cp .env.production .env.production.bak-<date>-<reason>`. Do not replace the whole host file.

## 4. Deploy

Report the results of steps 1 to 3 and get approval. Then follow "Deployment procedure" in
[docs/remote-session-deployment.md](../../../docs/remote-session-deployment.md) with these changes:

- Use `git archive <tag>` and the release directory `/opt/openbot/releases/$(git rev-parse <tag>^{commit})`.
  If the directory exists, do not write into it again.
- Tag the running image as `openbot-remote-api:before-<tag>` before the build.
- Build from the release directory. The document's build command passes its name, the tag commit,
  as `--build-arg OPENBOT_SOURCE_COMMIT`, and stops when the name is not a full commit SHA.
- Run the Compose commands with `-p openbot-remote`, and build and start only `remote-api`.

## 5. Verify

- `docker inspect openbot-remote-remote-api-1 --format '{{.Image}} {{.State.Health.Status}}'` shows
  the new image and `healthy`.
- `curl --fail https://signal.openbot.run/health/ready` succeeds.
- `curl --fail https://signal.openbot.run/health/live` shows `commit` equal to the tag commit (tags
  from before this field show no `commit`; use the checksums then).
- The container `sha256sum` of `/app/remote/api/src/*.ts` matches the tag.
- `docker logs --since <deploy time> openbot-remote-remote-api-1 2>&1 | grep -iE 'error|disabled|off|telegram|discord|slack'`
  shows no start errors, and shows each enabled feature. Do not print other log lines.
- Unauthenticated probes of each public route that the diff adds give 401 or 403, not 404 or 502.
  For example, `POST /v1/webhooks/x` with `content-type: application/json` gives 401.

If a check fails, use the rollback commands of the deployment document with the `before-<tag>` image.
Keep that image until the developer confirms the device checks of the deployment document.

## Report

One table: running commit before and after (from `/health/live`, or from checksums), image IDs, each secret key status, each regression area,
health, test result, and the checks that need a device or the developer.
