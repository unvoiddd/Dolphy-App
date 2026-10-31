# Upgrade surfaces

The path inventory behind the gates in `SKILL.md`. Facts and locations only — the judgement about
what a change to one of these costs stays in `SKILL.md`.

## `userData` files

Every one of these is read and rewritten in place by whichever build opens it last. Nothing copies
any of them before an upgrade. A renamed constant means the old file is never read again; a bumped
`version` with no read path means the old file is read as garbage and replaced with defaults.

| File | Constant | Version handling |
| --- | --- | --- |
| `openbot.db` | `DatabaseCore` in `src/backend/database/database-core.ts` | `schema_migrations`, gate A |
| `openbot-setup-v2.json` | `SETUP_FILE`, `src/main/application-services.ts` | `src/main/setup-store.ts` accepts only `version === 2`, silent default otherwise |
| `openbot-analytics-preference-v1.json` | `ANALYTICS_PREFERENCE_FILE`, `src/main/application-services.ts` | `src/main/analytics-preference-store.ts` |
| `openbot-update-preference-v1.json` | `UPDATE_PREFERENCE_FILE`, `src/main/application-services.ts` | `src/main/update-preference-store.ts` |
| `openbot-dynamic-island-preference-v1.json` | `DYNAMIC_ISLAND_PREFERENCE_FILE`, `src/main/application-services.ts` | `src/main/dynamic-island-preference-store.ts` reads `version` 1 and 2 forward into 3 |
| `openbot-main-window-state-v1.json` | `MAIN_WINDOW_STATE_FILE`, `src/main/index.ts` | `src/main/main-window-state.ts` accepts only `version === 1` |
| `openbot-browser-state-v1.json` | `BROWSER_STATE_FILE`, `src/main/application-services.ts` | `readBrowserState` in `src/backend/browser-state.ts` reads `version` 1 and 2 forward into 2 |
| `openbot-sidebar-layout-v1.json` | `SIDEBAR_LAYOUT_FILE`, `src/main/application-services.ts` | `src/backend/sidebar-layout-store.ts` |
| `openbot-team-server-v1.json` | `TEAM_FILE`, `src/main/application-services.ts` | frozen as the last pre-accounts build left it |
| `openbot-team-server-v2.json` | `TEAM_FILE_V2`, `src/main/application-services.ts` | `src/main/team-store.ts`; both files coexist so a downgrade still finds its host |
| `openbot-remote-servers-v1.json` | `REMOTE_SERVERS_FILE`, `src/main/application-services.ts` | `src/main/remote-server-stored-shape.ts` reads v1/v2 as v3, preserves unreadable entries, refuses unknown versions |
| `openbot-central-auth-v1.bin` | `CENTRAL_AUTH_FILE`, `src/main/application-services.ts` | `safeStorage`-encrypted; `src/main/central-auth-manager.ts` **throws** on anything but `version === 2` and `#initialize` catches that into `#clearStoredSession()`, so a bad shape signs the user out; undecryptable if `appId` or the signing identity changes |
| `openbot-remote-desktop-credential-v1.json` | `LEGACY_REMOTE_DESKTOP_CREDENTIAL_FILE`, `src/main/application-services.ts` | legacy, still read |
| `openbot-remote-desktop-runtime-v1.json` | `REMOTE_DESKTOP_RUNTIME_SECRET_FILE`, `src/main/application-services.ts` | `safeStorage`-encrypted; `src/main/remote-desktop-secret-store.ts` accepts only `version: 1` |
| `remote/connection-<stack pid>.json` in the dev runtime directory | `developmentRemoteConnectionPath`, `src/main/development-runtime-directory.ts` | development only |
| `bots.json` | `LEGACY_AGENTS_STATE_FILE`, `src/backend/agent-store.ts` | permanent name; imported once under command id `legacy-import:bots:v1` |
| `mailbox.json` | `src/backend/mailbox-store.ts` | permanent name |
| `sunshine-credentials.json`, `sunshine-state.json`, `sunshine-apps.json`, `moonlight-config.json`, `moonlight-data.json` | `src/main/sunshine-moonlight-runtime.ts` | written by the vendored runtimes, not by OpenBot |
| `avatars/agents/`, `agent-duplications/` | `src/backend/agent-store.ts` | directories under `userData` |
| `legacy-backup-v1/` | `DatabaseCore.backupLegacyFile` | one copy per named legacy file, `COPYFILE_EXCL`; **not** a general backup |

`openbot-data.json` in `src/main/maintenance-service.ts` is the user-initiated export manifest, not
persisted state.

One versioned file lives outside `userData`: `.openbot/skills-lock.json` under each agent workspace,
written by `src/main/skill-marketplace-service.ts`, which accepts only `version === 1`. It records
the marketplace skills installed into that workspace and is unrelated to the repository's own
`.agents/skills/`.

Derive this set rather than reading it off the table — see the `git grep` in gate B. The table is a
snapshot, and a serialization owner added after it was written will not be in it.

## Renderer `localStorage`

Kept in the Electron partition under `userData`, so it outlives the build that wrote it. Keyed by
string, not guarded by a `version` field, which is why the `git grep` in gate B does not reach it —
use the key-inventory diff there instead. Derive the set rather than reading it off this table.

| Key | Owner |
| --- | --- |
| `openbot:sidebar-pins:v1` | `src/renderer/src/features/sidebar/sidebar-pins.ts` |
| `openbot:sidebar-collapsed:v1` | `src/renderer/src/features/sidebar/sidebar-sections.ts` |
| `openbot:sidebar-people-order:v1` | `src/renderer/src/features/sidebar/sidebar-people-order.ts` |
| `openbot:left-panel-collapsed`, `openbot:left-panel-width` | `src/renderer/src/layout.tsx`, `layout-constants.ts` |
| `openbot:browser-panel-width`, `openbot:browser-pip-native-bounds` | `src/renderer/src/features/conversation/FilePreviewPanel.tsx`, `conversation-controller.ts` |
| `openbot:settings-panel-width` | `src/renderer/src/features/settings/settings-context.tsx` |
| `openbot:completion-sound-enabled` | `src/renderer/src/completion-sound.ts` |
| `openbot:analytics-app-version` | `src/renderer/src/App.tsx` |
| `openbot:landing-preview-ready`, `openbot:landing-preview-start` | `packages/contracts/src/landing-preview-messages.ts` |

The three `:v1` suffixes are the renderer's own version handling: bumping one to `:v2` abandons the
old entry rather than migrating it, so it resets that state for every installed user.

`sidebar-pins.ts` also carries `reownSidebarPinnedItems`, which rewrites a stored `bot-<uuid>` pin to
the matching `agent-<uuid>`. It is the reason gate B reads owners and not only keys: that migration
changed the value under an unchanged key, so a key-only diff reports it as clean.

## Mobile and shared team-client

The phone and the shared client keep their own state, on their own storage, with their own upgrade
cycle. Keys here are dot-separated, not colon-separated like the renderer's.

| Key | Owner | Note |
| --- | --- | --- |
| `openbot.mobile.session.v1` | `apps/mobile/src/features/auth/api/mobile-auth.ts` | `SecureStore`; an undecodable session is **deleted** (`deleteItemAsync`), signing the user out |
| `openbot.mobile.device-id.v1` | `apps/mobile/src/features/auth/api/mobile-auth.ts` | `SecureStore`; losing it abandons the device identity |
| `openbot.host-key.v1.<scope>.<fingerprint>` | `apps/mobile/src/features/workspace/model/trusted-host-keys.ts` | `SecureStore`; host trust, so resetting it re-prompts for every host |
| `openbot.workspace.v1.<scope>.<fingerprint>` | `packages/team-client/src/workspace-preferences.ts` | guards `value.version !== 1` |
| `openbot.remote-desktop.signal.v1` | `packages/team-client/src/remote-peer.ts`, `src/renderer/src/features/team/team-webrtc.ts` | shared by the desktop renderer, not mobile-only |

The middle two are built with a template literal, so **no key-literal query returns them** — they are
the standing reason gate B reads owner files as well as keys.

The paired-phones note under `## [0.5.0]` in `CHANGELOG.md` is what this surface looks like when
it breaks: agent identifiers changed, the phone still held the old ones, and the only remedy was for
the user to re-pair. Gate B is what should catch the next one before gate G has to apologise for it.

## `~/OpenBot`

Built in the `AgentStore` constructor, `src/backend/agent-store.ts`:

| Directory | Note |
| --- | --- |
| `~/OpenBot/Agents/<agent-id>` | the current workspace root |
| `~/OpenBot/Bots/<bot-id>` | the pre-rename root; permanent, still live after a v13-less restore |
| `~/OpenBot/Shared` | |
| `~/OpenBot/Downloads` | |

`src/backend/workspace-paths.ts` resolves four permanent prefixes — `~/OpenBot/Agents/<id>/`,
`OpenBot/Agents/<id>/`, `~/OpenBot/Bots/<legacy-id>/`, `OpenBot/Bots/<legacy-id>/` — and
`rebaseLegacyWorkspacePath` rebases in both directions, because migration v13's directory move gives
up on `EXDEV` or a permission error while the message paths have already been rewritten.

## Migration constants

All in `src/backend/openbot-database-schema.ts`:

| Constant | Value or meaning |
| --- | --- |
| `BASELINE_SCHEMA_VERSION` | `8` — the frozen baseline, `BASELINE_V8_SCHEMA_SQL` |
| `MIGRATIONS` | one contiguous entry per version from 8 up; read the last one from the release tag, never from here — a number written down here is stale the next time a migration ships |
| `LATEST_SCHEMA_VERSION` | derived from the last `MIGRATIONS` entry |
| `LATEST_SCHEMA_SQL` | `substituteOnce(BASELINE_V8_SCHEMA_SQL, BASELINE_REACTIONS_TABLE_SQL, V12_REACTIONS_TABLE_SQL)` |
| `substituteOnce` | throws unless the search string appears **exactly once**; handles one table substitution and no more |
| `validateMigrationRegistry` | throws on a non-contiguous version |
| `createLatestDatabase` | execs `LATEST_SCHEMA_SQL`, then stamps every `MIGRATIONS` entry as applied without running it |

Tests: `src/backend/openbot-database-schema-parity.test.ts` (the two build paths must agree),
`src/backend/openbot-database.test.ts` (including "rejects a database created by a newer
application").

## Team API protocol

The versions below are the ones that existed when this file was written. Match the frozen set with
the pathspecs in gate C rather than reading a version number off this table.

| Path | Status |
| --- | --- |
| `packages/contracts/src/team-protocol/v1.ts`, `v2.ts`, `v3.ts` | frozen codecs |
| `packages/contracts/src/team-protocol/v1-adapter.ts`, `v2-adapter.ts`, `v3-adapter.ts`, `v3-webrtc-adapter.ts` | registered adapters |
| `packages/contracts/src/team-protocol/fixtures/v1/` | `client-http-request.json`, `client-scope.json`, `host-compatibility.json`, `host-event.json`, `host-http-response.json` |
| `packages/contracts/src/team-protocol/fixtures/v2/` | `client-scope.json`, `event.json`, `file-open.json`, `host-compatibility.json`, `host-event.json`, `host-http-response.json`, `request.json` |
| `packages/contracts/src/team-protocol/fixtures/v3/` | `client-http-request.json`, `host-http-response.json` |
| `packages/contracts/src/team-protocol/current.ts` | where an additive capability goes |
| `packages/contracts/src/team-protocol/current-agent-keys.ts` | the only wire-to-app vocabulary translator |

`current-agent-keys.ts` maps keys (`bot`/`botId`/`bots`/`ownerBotId`/`recipientBotId`/
`recipientBotIds`/`senderBotId`/`typingBotId`), discriminant *values* (`kind: "bot"`, `origin:
"bot"`, `type: "bots-changed"`), and reverses the mapping inside `marketplaceSource`, where
`agentId` names a listing rather than the product agent. Sidebar layout payloads pass through
untouched — they already spell the agent `agent` on both sides.

## IPC channel list and its mirrors

`packages/contracts/src/ipc-endpoints.ts` declares every channel. Per the table in
`packages/contracts/AGENTS.md`:

| Mirror | What it is |
| --- | --- |
| `src/main/index.ts` and `src/main/ipc/` | the `handleTrusted` registrations, one file per domain |
| `src/preload/index.ts` | the `invoke` calls the renderer reaches |
| `src/renderer/src/preview/mock-openbot.ts` | the second implementation Storybook and the preview run against |

`src/main/ipc-channel-coverage.test.ts` links main and preload statically. The mock is covered by
`tsc` in both directions because it is annotated `: OpenBotDesktopApi`.

## Account Worker

| Path | Note |
| --- | --- |
| `apps/auth-api/migrations/` | `0001` to `0018`; applied **before** the new Worker deploys |
| `apps/auth-api/src/routes/v1/`, `v2/` | the response shapes installed desktop builds keep reading forever |
| `apps/auth-api/src/server/` | the handlers behind those routes |
| `apps/auth-api/test/*-migration.test.ts` | `email-challenge-delivery-migration`, `marketplace-migration`, `mobile-auth-migration` |
| `.github/workflows/ci.yml` | the CI deploy path: "Apply production D1 migrations" before the Worker deploy, one job |
| `scripts/deploy-auth-api.ts` | the manual deploy path: remote D1 migrations, build, then `wrangler deploy` |
| `apps/auth-api/package.json` | `deploy` and `deploy:test` — the entrypoints into that script |
| `apps/auth-api/wrangler.jsonc` | the D1 binding and environments both paths deploy against |

Two deploy paths, both ordered migrations-then-deploy. Neither one covers the other, and the
deploy-race reasoning in gate E only holds while *both* keep that order.

`auth:*` channels in `packages/contracts/src/ipc-endpoints.ts` are the desktop side of this contract.

## Updater and packaging

| Path | Fields that matter |
| --- | --- |
| `electron-builder.yml` | `appId` (`app.openbot.desktop`), `artifactName`, `electronUpdaterCompatibility`, `publish` owner/repo, `mac.extendInfo.ElectronTeamID` (`ZTRDTUL87R`), `extraResources` |
| `src/main/update-service.ts` | the four `UpdateAdapter` behaviours, documented on the type |
| `scripts/verify-update-artifacts.ts` | 700 MiB update artifact, 750 MiB DMG, manifest, blockmap, no voice model |
| `.github/workflows/release.yml` | macOS ZIP must be under 800,000,000 bytes **and** smaller than the `v0.1.21` ZIP |
