# EGM Setup & Workflow Guide

## Background

The **Elephant-Goldfish Model (EGM)** is an implementation of Spec-Driven
Development (SDD) based on Dave Rensin's foundational article
[Elephants, Goldfish, and the New Golden Age of Software Engineering](https://medium.com/@drensin/elephants-goldfish-and-the-new-golden-age-of-software-engineering-c33641a48874).

A general recommendation: start with the core philosophy before diving into the
tools. Going through the conversational sequence once or twice, especially if
you are unfamiliar with LLMs, will expose you to LLM quirks and develop your
intuition for using them.

If you do nothing else with EGM, do that a few times to see if you're getting
better responses out of the LLMs than you were before. Other than your time,
it's free, you don't have to install anything, and the instructions are labeled
1 to N.

If you like what that's doing for you, you'll start to notice some things. You
won't like copy-pasting your prompts all the time. You'll see some repetition
and wish the LLM could just do steps 1 to X on its own. The EGM framework is a
result of that. A bunch of people that practiced it for a while noticed some
emerging patterns and tried to make it easier to get started with fewer steps.

## Runtime Setup

The EGM skills and personas are packaged as a plugin for **Antigravity**, **Claude Code**, **Claude Cowork**, **OpenAI Codex**, and **ChatGPT Work** (see [Harnesses](harnesses.md) for installation instructions).

1.  **Skills**: The skills in `skills/` can be installed via the plugin/extension
    manifest or invoked via slash commands (e.g. `/egm-brainstorm`,
    `/egm-draft`, `/egm-review`).
2.  **Personas**: The prompts in `agents/` define isolated system instructions
    for subagents.

## Main Workflow

While EGM has a "main workflow", this process is not a prescription. The skills
are designed to be fluid. You can and should reach for them out of order if that
suits your needs.

The general goal is to have:

*   Code that does what you want
*   Documentation that explains why the code exists the way it does

You may find yourself going back and forth between skills to solve your problem
at hand, and this is encouraged. Do not forcibly use the design-first process if
you believe you can one-shot a simple bug fix. Use the right tool for the job.

Use whichever skill serves your immediate bottleneck.

### Core Loop

The "core" workflow looks like:

1.  **`/egm-brainstorm`**: Start an upstream ideation session. This command
    prompts the developer to clarify the concept, then spawns a 6-lens Goldfish
    swarm to stress-test the concept and generate a structured `concepts_brief.md`
    artifact.

2.  **`/egm-draft`**: Drafts a design doc. This command turns a concept brief or
    initial idea into a formal markdown design doc under `docs/designs/`, section
    by section.

3.  **`/egm-review`**: Reviews a design with **Goldfish**. Performs a
    multi-stage review of a design doc in a fresh, zero-context session to
    validate its completeness, clarity, self-containment, and readiness.

    This will provide *a lot* of feedback by design. Take what you think is
    relevant. Ignore the rest.

4.  **Human Review**: Send a Pull Request with your design to another human. EGM
    does not replace human oversight, but encourages it. Human reviews are
    essential for ensuring long-term product consistency and prioritization.

5.  **Implement**: Tell AI to build to the plan. The instructions on how to
    implement it should be in your design doc. If the LLM makes a minor mistake,
    feel free to fix it yourself, or ask the LLM to make point fixes. If it is
    wildly off, update the design doc and try again.

    > NOTE: Every team is going to have different skills that are relevant to
    > their implementation phase. Readability and style guides, profiling tools,
    > testing strategies, etc. EGM is not opinionated here. Add your skills to
    > your implementation plan!

## Drift Detection

To ensure that your design specifications and active codebase remain
synchronized over time, EGM provides a drift detection skill:

*   **`/egm-drift-report`**: Scan your design directory and referenced code for
    drift. Any detected divergence should be reconciled. Either update the doc,
    or the code. Human picks which.

We recommend running this once per week. If you have a regular maintenance
rotation, incorporating this as a weekly ritual is useful.

## Bootstrapping Existing Codebases

### Structure

We recommend EGM project directories look something like this:

```text
path/to/project
├── _agents/                   # (optional, but recommended)
│   └── skills
│       └── <project>_elephant # Custom skill to load project context.
│           └── SKILL.md
└── docs/
    └── designs/               # EGM design directory
        ├── SITEMAP.md         # An index of design artifacts
        └── FEATURE.md         # A detailed design specification
```

### Bootstrapping

`/egm-bootstrap <path/to/project>` automatically generates, formats, and
performs a first-pass verification of READMEs across a directory tree to
establish codebase context. Humans should still review this output. It does this
using a bottom-up, recursive summarization process:

1.  **Leaf Directories**: For each leaf directory, generate a `README.md` that
    explains the directory's purpose and documents the function of each file.

2.  **Parent Directories**: Move up the tree recursively, generating parent
    `README.md` files by summarizing the subdirectories' READMEs.

3.  **Root Directory**: Compile the root `README.md` to provide a complete,
    high-level map of the system's architecture.

The structured context hierarchy lets Elephants navigate codebases more easily.
EGM prefers humans having control of these outputs, which is why this skill
exists. If all of your design artifacts already reference all the code, READMEs
may be unnecessary.

### Project Context Skill (Optional)

You can create a custom `<project>-elephant` skill specifically for your
project. This skill does not need to be complicated. It should tell the LLM
about your project, and give it links to the context you think is most relevant.
That's all.

This skill can be a handy way to give LLMs references to code paths, documents,
and important designs quickly.

As your codebase grows and more design documents are archived, this context
loader allows the Elephant to retrieve relevant architectural history, enabling
more informed design discussions and precise feature implementations.
