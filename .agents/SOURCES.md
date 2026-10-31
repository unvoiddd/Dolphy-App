# مصادر المهارات المثبتة

91 مهارة في `.agents/skills/`. من أصل 81 مُدارة عبر `skills-lock.json`؛ العشر الباقية نُسخت يدوياً (مؤشَّرة ✋) فلا يحدّثها `npx skills update`.

| المصدر | العدد | المهارات |
|---|---|---|
| `thedotmack/claude-mem` | 31 | agent-cost-report, babysit, backup-setup, build, build-and-release, ccs-align, childhood, cloud-sync, design-is, do, handoff, how-it-works, knowledge-agent, learn-codebase, make-plan, mem-search, mem-setup, mode-creator, oh-my-issues, owner-goals, pathfinder, release, request-progress, screenpipe, smart-explore, standup, timeline-report, version-bump, weekly-digests, what-the, wowerpoint |
| `obra/superpowers` | 15 | diagnosing-superpowers, dispatching-parallel-agents, executing-plans, finishing-a-development-branch, receiving-code-review, requesting-code-review, subagent-driven-development, systematic-debugging, test-driven-development, using-git-worktrees, using-superpowers, verification-before-completion, writing-plans, writing-skills, ✋ superpowers-brainstorming |
| `JetBrains/thinkrail` | 12 | asking-user-questions, brainstorming, choosing-a-workflow, importing-a-codebase, managing-mcp-servers, setting-up-a-project, shipping-a-pr, spec-graph, starting-a-new-project, todos, writing-specs, writing-workflow-skills |
| `nightly-labs/openbot` | 9 | openpanel, prototype, release-upgrade-safety, ship, signal-deploy, smoke, smoke-and-ship, triage, ✋ openbot-babysit |
| `google/elephant-goldfish-model` | 7 | egm-bootstrap, egm-brainstorm, egm-draft, egm-drift-report, egm-resolve, egm-review, egm-review-loop |
| `milind-soni/OpenMausBot` | 5 | create-verification-skill, data-dashboards, phone-harness, verify-omb, windows-release |
| `HKUDS/DeepTutor` | 5 | ✋ docx, pdf, pptx, xlsx, skill-creator |
| `carere/zaidan` | 4 | shadcn-to-zaidan, ✋ zaidan, ✋ react-to-solid, ✋ biome-anti-slop |
| `elayadesign/ai-design-skills` | 1 | landing-page-design |
| `pbakaus/impeccable` | 1 | impeccable |
| `unclebob/ub-mode` | 1 | ub-mode |

## إعادة التسمية

تعارضت أسماء مهارتين عند التثبيت، و`skills add` كان يستبدل إحداهما بصمت، فأُبقيت النسخة الأخرى باسم مُسبوق:

- `brainstorming` — محفوظة من `JetBrains/thinkrail`؛ نسخة `obra/superpowers` باسم `superpowers-brainstorming`.
- `babysit` — محفوظة من `thedotmack/claude-mem`؛ نسخة `nightly-labs/openbot` باسم `openbot-babysit`.

عند التحديث تُستبدل المهارات المُدارة من `skills-lock.json` فقط، فلا تُفقد النسخ المُعاد تسميتها ولا المنسوخة يدوياً.

## ملفات إضافية

- `.agents/agents/`, `docs/`, `resources/`, `assets/`, `plugin.json`, `README.md`, `TESTING.md` — منسوخة من `google/elephant-goldfish-model` لأن مهارات EGM تشير إليها بمسارات نسبية.

## ما لا تعمل به الملفات وحدها

- **مهارات `claude-mem` الـ31**: تحتاج حزمة claude-mem وقاعدة بياناتها وMCP؛ الملفات لا تسجّل ذاكرة ولا تسترجعها.
- **مهارات openbot**: `release-upgrade-safety` و`prototype` و`signal-deploy` تشير إلى مسارات داخل مستودع openbot نفسه؛ تحتاج checkout للمستودع. حزمة `pstack` (~80 مهارة) لم تُثبَّت، ومنهجيتها يغطيها `ub-mode`.
- **`ub-mode`**: يشترط `crapper`, `dryer`, `mutator` (غير مثبتة).
- **مهارات EGM السبع**: مضبوطة على `disable-model-invocation: true`، أي تُستدعى بالاسم من المستخدم. و`egm-review` / `egm-review-loop` تحتاجان subagents بسياق جديد.
