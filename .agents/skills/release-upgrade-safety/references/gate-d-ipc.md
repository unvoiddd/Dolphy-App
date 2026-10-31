# Gate D — IPC channels — `packages/contracts/src/ipc-endpoints.ts`

Triggered by a change to the channel list **or to any of its mirrors** — `src/main/index.ts`,
`src/main/ipc/`, `src/preload/index.ts`, `src/renderer/src/preview/mock-openbot.ts`. Deleting a
handler or an `invoke` breaks a live channel without touching the list at all, and the coverage
test below is what catches it, so a gate that only watched the list would skip the one run that
would have found it.

Renderer and main ship in one binary, so a channel rename is **not** an upgrade hazard — nothing
older ever calls it. The hazard is drift between the list and its hand-written mirrors, which is a
runtime rejection in the build you are about to sign. Confirm all of them moved together, per the
table in `packages/contracts/AGENTS.md`: the handlers that `registerIpcGroups` binds from
`src/main/ipc/`, the `bridgeGroup` maps and hand-written calls in `src/preload/index.ts`, and
`src/renderer/src/preview/mock-openbot.ts`, the second implementation Storybook and the preview run
against.

```bash
bun run test:desktop -- src/main/ipc-channel-coverage.test.ts
```

That test links main and preload. The mock is covered by `tsc` in both directions, so what is left
for you to judge is its *behaviour* — a mock method that satisfies the type by returning an empty
array is a story that silently shows nothing.
