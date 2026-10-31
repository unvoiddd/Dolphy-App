---
name: prototype
description: Build several different design directions (3 by default) for a feature or issue as Storybook stories, show them to the user, and let the user choose one. Use when the user writes `$prototype <feature>`, "prototype X", or asks for design variants, options, or directions before implementation.
---

# Prototype

Make a small number of different, clickable directions for one feature in
Storybook. Show them to the user. The user chooses the direction. Do not
implement the feature in product code before the user chooses.

The usual call is short, for example `$prototype new pricing page`. Get the
missing details from the issue and the code. Ask only when you cannot
continue without the user.

## Inputs

- **Subject**: the text after `$prototype`, an issue number, or an issue URL.
- **Count**: 3 variants, unless the user gives a different number.
- **Style**: follow the repository style and reuse its components, unless the
  user says otherwise (for example "go wild", "new visual style", "ignore the
  current design"). When the user sets the style aside, say which parts you
  changed.

## Step 1 — Understand the subject

1. When the subject is an issue, read it: `gh issue view <n> --comments`.
   Otherwise search open issues for the subject:
   `gh issue list --search "<subject>" --state open`. Use a match only when
   it is clearly the same feature.
2. Find the current code for the feature, when it exists. Read it and its
   existing stories. Note what the user can do now, and the states it has
   (empty, loading, error, long data, narrow width).
3. Find the surface:

   | Surface | Story location | Stylesheet |
   | --- | --- | --- |
   | Desktop renderer, shared UI | `src/renderer/stories/` | Global; `.storybook/preview.tsx` loads it |
   | Public web (`apps/auth-api`) | Beside the page, in `apps/auth-api/src/components/<area>/`. For a new page, use `landing/` | Import `apps/auth-api/src/styles.css` in the story, as `DiscordConnectPage.stories.tsx` does |

   A pricing page, landing page, or article is public web. A dialog, panel,
   sidebar, or settings screen is desktop.

   A public web page with the site header needs a router stand-in. Copy the
   `WithRouter` pattern from `AgentTemplatePage.stories.tsx`.
4. Read `packages/ui/AGENTS.md` and `src/renderer/AGENTS.md`. For public web,
   also read `apps/auth-api/AGENTS.md`.
5. Take a reference screenshot of the place where the feature lives in the
   current app. For desktop, use the `App` story (`app--expanded-sidebar` and
   the other `app--*` ids), the real container story (for example
   `settings-serversettingsmodal--*`), or the dev app with
   `bun run dev:automation screenshot`. For public web, use the page story.
   Save it as `reference.png` beside the variant screenshots (Step 5).
6. Write down your assumptions in one short list. You show it to the user in
   Step 6.

## Step 2 — List what to reuse

Before you write UI, list the parts that already exist:

- Primitives from `@openbot/ui` (`packages/ui/src/`).
- Feature components from `@openbot/ui/features/<domain>/...`.
- For public web: existing site components and classes in
  `apps/auth-api/src/components/` and `apps/auth-api/src/styles.css`. Import
  only explicit `@openbot/ui/...` subpaths there. The `@openbot/ui` barrel
  fails the Worker typecheck.
- Data that already exists, such as plans, prices, and limits in
  `packages/contracts`. Use the real values, not invented ones.
- Fixtures and mocks: `src/renderer/stories/*fixtures*`,
  `src/renderer/src/preview/`, and the fixtures of related stories.
- Design tokens and existing CSS classes. Do not add new colors, font sizes,
  radii, or spacing values when a token exists.
- Similar screens in the app. A new screen must look like it belongs next to
  them.

References are the real app and real components only: `App`, `WebWorkspace`,
the real containers, and stories of one component. Do not copy layout from a
story that builds its own shell, header, sidebar, chat panel, or settings
menu. Such a copy keeps a design that the app no longer has.

## Step 3 — Choose the directions

Each direction must be a different answer to the problem, not a different
color. Change at least one of these between variants:

- Information hierarchy: what the user sees first.
- Layout: cards, table, list and detail, single column, steps.
- Interaction model: inline edit, dialog, wizard, progressive disclosure.
- Density: compact for experts, spacious for first use.

Give each direction a letter, a short name, one sentence for its idea, and
one sentence for its main cost. Example for a pricing page:

| Variant | Idea | Cost |
| --- | --- | --- |
| A. Plan cards | Three cards side by side; the recommended plan is in the middle | Feature differences are hard to compare |
| B. Comparison table | One table with a row per feature | Dense on narrow screens |
| C. Guided choice | Two questions, then one recommended plan | One more step before the price |

Do not ask the user to approve the directions. Build them.

## Step 4 — Build the variants

### Start from the real app

Render the real app or the real container, and hand-build only the new part.
Never copy an existing header, sidebar, menu, settings navigation, or composer.

- A feature in the shell (page rail, sidebar, chat header, chat, composer):
  render `PrototypeApp` from `src/renderer/stories/prototype-support.tsx`.
  Put the new part into the real DOM with `createAppDom` and
  `AppSlot` (`header`, `topbar`, `rail`, `sidebar`), open a chat with
  `openChat`, and add data with the `setup` prop (for example
  `mock.api.agent.channelCommand`).
- A feature in a settings section: render the real dialog on that section.
  Server settings: `ServerSettingsStory` from
  `src/renderer/stories/server-settings-story.tsx`, with `initialSection`.
  App settings: `SettingsModal`, as `SettingsModal.stories.tsx` does.
- A feature in the chat timeline: `StoryConversation` from
  `src/renderer/stories/conversation-story-support.tsx`.
- A new dialog or panel that the app does not have yet: open it over
  `PrototypeApp`, so the user sees it in the window.

When the real app cannot show a state without product code, show the new
component alone, and say so in Step 6.

### Files

Put each prototype in its own files. Do not change product code, shared
components, contracts, or the i18n catalog for a prototype.

- Stories: `<Feature>Prototype.stories.tsx`.
- Prototype components, when the story file becomes long:
  `<feature>-prototype.tsx` in the same directory.
- Styles, only when tokens and existing classes are not sufficient:
  `<Feature>Prototype.css` in the same directory. Scope each rule under a
  class of the prototype.

Use this story structure:

```tsx
const meta = {
  title: "Prototypes/Pricing page",
  parameters: { layout: "fullscreen", a11y: { test: "error" } },
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

/** Three plan cards. The recommended plan is in the middle. */
export const PlanCards: Story = { name: "A. Plan cards", render: () => <PlanCardsVariant plans={PLANS} /> };
/** One table with a row for each feature. */
export const ComparisonTable: Story = { name: "B. Comparison table", render: () => <ComparisonTableVariant plans={PLANS} /> };
/** Two questions, then one recommended plan. */
export const GuidedChoice: Story = { name: "C. Guided choice", render: () => <GuidedChoiceVariant plans={PLANS} /> };
```

Give each variant component a `Variant` suffix, so its name is not the same as
the story export. A desktop variant in the shell renders the real app and adds
only its part:

```tsx
/** A. Two arrows at the start of the chat header. */
export function HeaderArrowsVariant() {
  const app = createAppDom();
  return (
    <div class="chat-history-prototype" ref={app.attach}>
      <PrototypeApp />
      <AppSlot app={app} slot="header">
        <HistoryArrows />
      </AppSlot>
    </div>
  );
}
```

Rules:

- All variants use the same fixture data, so the user compares design, not
  content. Use realistic data: real plan names, long names, many rows.
- Make each variant interactive with local signals or a store. A button that
  does nothing is acceptable only when its result is outside the feature.
- When a state is important to the decision (empty, error, loading), add it
  as an extra story with the same letter, for example `A2. Plan cards, empty`.
- Draft new copy as literals. `no-hardcoded-ui-text.grit` does not check
  stories, prototype files beside them, or `apps/auth-api`. On desktop, use
  existing i18n keys when they fit; the chosen variant moves its other text
  to catalog keys in Step 7.
- When you reuse a shared class, check its media queries at phone width.
  Some site classes, such as `compare-table`, change layout below 640 px.
- Keep `a11y: { test: "error" }`. Fix each accessibility violation. Use real
  roles, labels, and focus order.
- Do not add `play` functions or `data-testid`. `check:ui` rejects them.
- Do not add dependencies.
- Do not add tests for a prototype.

## Step 5 — Check and capture

Run these checks one at a time:

1. `bunx biome check --write --max-diagnostics=none <new files>`
2. The one typecheck project for the surface:
   `bun run typecheck:renderer` for `src/renderer/stories/`, or
   `bun run typecheck:api` for public web.
3. Run `bun run dev:status`. When it lists a Storybook for this worktree,
   reuse it. Otherwise start Storybook with the Bash background option:
   `env -u ELECTRON_RUN_AS_NODE bun run storybook --no-open`. Use the port
   that it prints in `Storybook: http://localhost:<port>`. Do not pass
   `--port`. When Storybook exits with "Port <n> is not available", another
   process holds the port that the wrapper chose. Start again with
   `OPENBOT_STORYBOOK_PORT=<a free port>` before the command.
4. Open each variant at
   `http://localhost:<port>/iframe.html?id=<story-id>&viewMode=story`.
   The story ID is the title and the export name in kebab case, for example
   `prototypes-pricing-page--plan-cards`.
5. Take a screenshot of each variant with the Playwright or Chrome DevTools
   tools. Save them in `.openbot-build/prototypes/<feature>/`
   (git ignores this directory), for example `a-plan-cards.png`. Use a
   desktop width (1440 px) and, for public web, also a phone width (390 px).
   The desktop stylesheet sets `overflow: hidden` on `html` and `body`, so a
   full-page screenshot shows only the viewport. Before each screenshot,
   resize the viewport height to the `scrollHeight` of `#storybook-root`.
6. Compare each screenshot with the reference screenshot from Step 1.
   Everything outside the new part must be the same. When the shell, a
   header, or a menu looks different, the variant copies an old design:
   render the real one.
7. Look at each screenshot. Fix broken layout, clipped text, overlap, and
   console errors that the prototype causes before you show the variants.
   Report errors that also occur without the prototype; do not fix them.

Leave Storybook running, so that the user can open the variants. When a
subagent did the work, its background Storybook can stop when the subagent
ends. Before you send the links, open one. When it does not load, start
Storybook again.

## Step 6 — Show the variants

Write one message:

1. Your assumptions from Step 1, as a short list.
2. One table: variant, idea, strengths, costs, Storybook link
   (`http://localhost:<port>/?path=/story/<story-id>`).
3. The screenshots, embedded with their absolute paths.
4. Your recommendation, in one sentence, with the reason.
5. One question: which direction, or which mix (for example "B with the
   header of A").

Then stop. Do not commit, push, or open a PR unless the user asks.

## Step 7 — After the user chooses

- **More rounds**: when the user asks for changes or a mix, change the
  prototypes and repeat Steps 5 and 6. Remove the variants that the user
  rejected.
- **Implement**: when the user tells you to build the chosen direction,
  build it in product code with the normal repository rules: shared UI in
  `packages/ui`, text as `docs/i18n.md` says for the surface, release notes in
  `changelog.d/`, and focused checks only. Then delete the prototype files,
  or change the chosen story into the feature's normal story under its
  domain title. Do not keep `Prototypes/` stories in a PR unless the user
  asks for them.
- **Reject**: when the user rejects all directions, delete the prototype
  files. Do not keep `Prototypes/` or `Concepts/` stories on `main`: a later
  agent copies them as references.
