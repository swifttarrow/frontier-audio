# Create Implementation Plan (from Tech Spec)

Create a detailed **implementation plan** from an approved **technical specification**. Work interactively: validate the spec against the codebase, resolve ambiguities, and only then write phased execution steps with verifiable success criteria.

## Relationship to other artifacts

| Artifact | Role |
|----------|------|
| **Tech spec** | Primary input: architecture, data model, APIs, auth, workflows, NFRs, risks, open questions, optional implementation phases. |
| **PRD** | Upstream of the tech spec; use only if the spec is incomplete or you need product intent. |
| **Research** | Optional corroboration for risky or novel areas called out in the spec. |

The plan turns **how** (spec) into **what to change in the repo, in what order, with how we verify**.

## Initial Response

If no tech spec is provided, ask for:

1. **Tech spec** — file path (e.g. `docs/tech-spec-….md`) or full markdown paste
2. **Optional**: research docs, ticket links, or PRD excerpt if the spec references unresolved product questions
3. **Preferences** that affect sequencing or depth (e.g. ship smallest slice first vs big-bang, strictness on test coverage)

## Process

### Step 1: Context Gathering

1. **Read the tech spec end-to-end** and internalize every section (summary, goals/non-goals, context, architecture, data model, APIs, auth, workflows, NFRs, risks, open questions, optional phases).
2. **Build a coverage map**: for each major spec item, note whether it implies new code, schema changes, config, or ops work.
3. **Research the codebase**: locate files, patterns, and existing behavior that implement or contradict the spec.
4. **Cross-check**: flag spec decisions that conflict with the repo, duplicate existing systems, or need clarification.
5. **Surface open questions from §11 (or equivalent)** of the spec: either resolve with the developer, propose a default labeled **Assumption**, or block the plan until answered.

Present a short, informed summary and **focused questions** before drafting structure.

### Step 2: Research & Discovery

After clarifications:

- Spawn parallel research where the spec is thin, high-risk, or untested against the codebase.
- Cite concrete files, patterns, and conventions you will follow.
- Offer **2–3 options** only where the spec explicitly allows alternatives or where implementation reality forces a fork; otherwise execute the spec’s chosen approach.
- **Confirm** any material deviation from the written spec with the developer before writing the plan.

### Step 3: Plan Structure

Propose phases and get feedback before writing full detail. Align with the spec’s **optional implementation phases** when present; otherwise derive phases from coherent vertical slices or dependency order.

At minimum, confirm against the spec:

- Architecture and component boundaries
- Data model / migrations / API contract changes
- AuthN/AuthZ touchpoints
- New dependencies or tooling
- Rollout, feature flags, and risk mitigations from the spec

### Step 4: Write the Plan

Save to `docs/plans/YYYY-MM-DD-description.md`:

```markdown
# [Feature] Implementation Plan

## Overview
[Brief description and how it implements the tech spec]

## Tech spec traceability
- **Spec**: `path/to/tech-spec.md` (or pasted reference)
- **Maps to spec sections**: [e.g. Architecture → Phase 1–2; Data model → Phase 1; APIs → Phase 2]

## Current State Analysis
[What exists in the repo, constraints discovered, gaps vs the spec]

## Desired End State
[Specification-aligned target and how to verify]
[One coherent end state; use phased checkpoints toward it—do not split into unrelated “MVP vs v2” unless the spec explicitly does.]

## What We're NOT Doing
[Out of scope—mirror spec non-goals and anything explicitly deferred]

## Phase 1: [Name]
### Overview
[What this phase accomplishes relative to the spec]

### Changes Required
**File**: `path/to/file.ext`
**Changes**: [Summary]

### Success Criteria
#### Automated Verification:
- [ ] `make test`
- [ ] `make lint`

#### Manual Verification:
- [ ] Feature works as expected
- [ ] Edge cases verified

**Note**: Pause for human confirmation after this phase before proceeding.

---

## Phase 2: [Name]
[Same structure...]

## References
- Tech spec: `path/to/tech-spec.md`
- Research (if any): `docs/research/[relevant].md`
- Ticket (if any): [reference]
```

### Step 5: Iterate

Present the plan and refine from feedback. Do at least one explicit pass on **technical tradeoffs and alternatives** before finalizing.

## Recording Major Decisions

Capture major decisions in `developer-log.md` so an outsider can follow the reasoning over time.

When to log:

- Architecture or integration strategy changes (including vs the written spec)
- Data model, API, or contract decisions
- New dependencies or tooling choices
- Scope cuts or additions that change delivery strategy
- Risky tradeoffs (performance, reliability, security, UX)

If `developer-log.md` does not exist, create it.

Entry format:

```markdown
## [YYYY-MM-DD] [Short decision title]

**Context:** [What decision was needed and why]
**Options considered:** [Option A, Option B, and key tradeoff]
**Decision:** [Chosen option]
**Rationale:** [Why this option was selected]
**Impact:** [What changes as a result]
**Owner:** [Developer / Agent + developer confirmation]
```

Keep entries concise and focused on major decisions only.

## Guidelines

- **Spec-first**: The tech spec is the default source of truth for design; challenge it only with evidence from code or the developer.
- **Be skeptical**: Question vague spec bullets, unmapped PRD scope, and “TBD” items left unresolved.
- **Be interactive**: Get buy-in at structure and before locking contentious technical choices.
- **Be thorough**: File-level pointers, measurable success criteria, and explicit handling of spec open questions.
- **No open questions in the final plan**: Either resolved, logged as assumptions with owner, or deferred with a clear follow-up artifact.
