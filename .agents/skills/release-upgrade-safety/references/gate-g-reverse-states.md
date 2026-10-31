# Gate G — Reverse states and the changelog

Always triggered. There is no path that exempts a release from these.

- **Any state this release lets a user enter but not leave is a bug, not a release note.** Snooze
  needs unsnooze, pause resume, revoke reconnect, mute unmute. Walk the new user-facing states in
  the diff and name the exit for each.
- **Anything requiring a user action after upgrade goes in the release notes in bold** — a
  `changelog.d` file before the bump, the `CHANGELOG.md` section after it. The
  precedent is the paired-phones note under `## [0.5.0]`: it states the action, why
  the upgrade forced it, and what is *not* lost. Copy that shape. A user who has to act and is not
  told files a data-loss bug.
