# Test Plan: EGM Review & Subagents

Frontmatter and link checks run in `pytest tests/unit` (see [TESTING.md](../../TESTING.md)).

## Protocol Verification Checklist

When evaluating or exploring `egm-review` interactively in chat, verify:

-   **Step 1 (Intake):** Gathers `TargetFile` and optional `GoldfishContext`.
-   **Step 2 (Semantic Split):** Discovers the split line between Feature Spec
    and Detailed Implementation Plan.
-   **Step 3 (Flow Selection):** Offers Full EGM Flow vs. Individual Stages (Steps 5, 6, 7, 8, 9).
-   **Subflows:** Properly delegates Zoom Out, Comprehension, Critic Review, and Readiness Test.
