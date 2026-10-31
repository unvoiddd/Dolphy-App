# FAQ

#### Q: Are there alternatives?

**A**: Yes. Many.

EGM is one of the ways teams are making LLMs work better. It is not *the* way.
There are others with different trade-offs. Regardless of the tool you choose,
please remember: *You own the outcomes.*

#### Q: What concrete artifacts does the EGM process produce?

**A**: EGM produces three core deliverables:

*   **Design Document**: A peer-reviewed technical specification that captures
    intent, system invariants, and strategic direction.
*   **Implementation checklist**: Identifies the files to create or modify,
    the planned changes, and how to verify them.
*   **Enriched Context Loops**: Colocated Markdown documentation (such as README
    hierarchies) that provides high-quality context for future development
    loops.

#### Q: Will SWEs become obsolete under EGM?

**A**: In our opinion? No. However, code generation will be increasingly
delegated to automated agents, shifting human engineering focus toward governing
intent and reviewing architectural decisions captured in design documents.

#### Q: Will the increased volume of design documentation create a human review bottleneck?

**A**: It can. Reviewing designs adds work, and excessive documentation can
become a bottleneck. The aim is to catch important problems before implementation;
whether that saves time depends on the change and the quality of the review.

We recommend putting an emphasis on prioritization, at least for now. Use EGM
for "What's important now?", instead of everything, to avoid overloading your
reviewing and release pipelines.

#### Q: Does this workflow degrade developer velocity?

**A**: It depends. Design and review take time and may not pay off for a small,
well-understood change. For uncertain or consequential changes, they may reduce
rework by exposing missing decisions earlier.

Compare total time, useful findings, and rework on your own tasks. Completing
the EGM workflow alone does not demonstrate a productivity improvement.

#### Q: Is expecting design documentation to be complete enough for a zero-context agent realistic?

**A**: Sometimes, but not always. EGM's approach captures strategy and an
execution plan, with Goldfish agents improving the quality, but LLMs are still
non-deterministic.

You need to use your judgment the same way you would for coaching a new software
engineer. If it is making a minor mistake, adjust it. If it is wildly off base,
maybe you need to go back to the drawing board and correct your design. This is
part of the process and gets easier the more you use it.

#### Q: Should I open big pull requests?

**A**: No. Generally, the answer for this (and many other questions) is "The
answer is the same as when you built things without AI."
