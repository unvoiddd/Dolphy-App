# Elephant-Goldfish Model (EGM)

## Find the right document

| Location | Purpose |
| --- | --- |
| [Getting started](getting_started.md) and [FAQ](faq.md) | Installation and everyday use. |
| [Harnesses](harnesses.md), [Claude](claude.md), and [Codex](codex.md) | Host-specific installation and execution. |
| [Design documents](https://github.com/google/elephant-goldfish-model/blob/main/docs/designs/README.md) | Architecture, workflow rationale, and implementation plans. |
| [Shared runtime resources](../resources/README.md) | Authoritative execution rules and review standards. |
| [Testing playbook](https://github.com/google/elephant-goldfish-model/blob/main/TESTING.md) | Commands and evidence requirements. |
| [PR #103 verification reports](https://github.com/google/elephant-goldfish-model/pull/103) | Detailed harness results and known verification gaps. |

`docs/` explains the system to contributors and users. `resources/` contains
rules that the runtime reads. Link to those rules when explaining them here;
keep requirements in their canonical resource files.

AI has changed software development. Everything is different now and we all know
it.

The **Elephant-Goldfish Model** (EGM) is one way teams are adapting to the
change. It is a protocol for shipping high quality software quickly, wrapped in
a metaphor to make it easy to remember. Dave Rensin's article,
[Elephants, Goldfish, and the New Golden Age of Software Engineering](https://medium.com/@drensin/elephants-goldfish-and-the-new-golden-age-of-software-engineering-c33641a48874),
goes into this in depth. If you haven't read that, start there!

## Elephant-Goldfish Philosophy

### The Elephant

The **Elephant** remembers everything.

Sometimes we want LLMs to know all about our systems. Their history, the user
journeys, the logical components, and so on. And we want to be able to discuss
how all this fits together naturally.

That isn't possible by looking at code alone. We need to make all of this
information available to LLMs in a way they can navigate cheaply. When we do,
we can ask questions, challenge assumptions, and force ourselves to justify
decisions fast, which makes changing the system that much easier.

When we give LLMs access to a context-rich description of our systems and ask
them to explore our problems, we call that an **Elephant**.

### The Goldfish

**Goldfish** have three-second memories [^not-really].

LLMs are not deterministic. Sometimes they hallucinate, resulting in plans or
code that is obviously broken. Trusting LLMs to build our systems without
checking for hallucinations is a recipe for a bad time.

Naively, we could have humans review every change. It works, but it is
expensive and quickly becomes a bottleneck. Another way is to have one LLM
review another. If we ask LLMs to review plans, code, or other artifacts
*without* access to how those things were generated, we reduce hallucinations.

When we ask LLMs to review output this way, limiting the context they have
access to, we call that a **Goldfish**.

### The Humans

Imagine you have to say this to your manager: *"Sorry! My AI tool just pushed
broken code to production..."*. It's not the tool that did that, it was you that
did that.

Or maybe a different scenario: *"The AI did my project! Promote me!"*. This is
just as silly.

AI is a tool, not a decision-maker. Developers must actively guide design
discussions, challenge suggestions, and make decisions rather than letting AI
drive.

EGM leans into this: **Humans** own the outcomes.

### Benefits

EGM emphasizes learning how to use LLMs as a tool and explains how to navigate
their strengths and weaknesses.

*   **Velocity Gains:** Features ship faster. Front-loading alignment into design
    specifications significantly reduces downstream rework and review comment
    loops.
*   **Accuracy:** Investment in front-loaded design discussions creates deep,
    verified guidelines that reduce LLM hallucinations. Being specific and
    detailed about exactly what we want to be implemented gives AI an explicit
    reference to check against.
*   **Team Benefits:** Project handoffs, onboarding, issue triage, and all the
    other human processes that may require a whiteboard get easier. When LLMs
    know where features are, how they're built, and the intent behind them,
    humans can learn about and contribute to systems quickly.

## Documentation & Guides

*   [Getting Started](getting_started.md): Installation and core workflow.
*   [Codex](codex.md): Plugin installation and native workflow verification.
*   [Claude Code](claude.md): Plugin resources and isolated reviewers.
*   [Frequently Asked Questions (FAQ)](faq.md): Common questions and answers.
*   [Framework Architecture](https://github.com/google/elephant-goldfish-model/blob/main/docs/designs/architecture.md): Detailed component
    design and lifecycle.
*   [Contributing Guidelines](https://github.com/google/elephant-goldfish-model/blob/main/CONTRIBUTING.md): How to contribute new skills,
    personas, and standards.

[^not-really]: It's a
    [myth](https://www.biology.ox.ac.uk/article/goldfish-show-a-keen-ability-to-estimate-distances)
    that Goldfish have three-second memories. But that would ruin the metaphor!
