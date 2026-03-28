# Developer Log

Major product and technical decisions are captured here to preserve implementation context.

Use this format for new entries:

## [2026-03-28] Jarvis implementation plan and client stack precedence

**Context:** The repo had research favoring Expo/React Native for the mobile client, while `docs/specs/1-initial.md` mandates Kotlin Android, PTT-only UX, and WebSocket to a grounded voice backend.
**Options considered:** Re-open client runtime (RN vs Kotlin) vs treat the written spec as approved scope.
**Decision:** Follow **`docs/specs/1-initial.md`** for implementation; execute phased plan in **`docs/plans/2026-03-28-jarvis-implementation.md`**. Client-runtime research remains background only unless product changes the spec.
**Rationale:** Plan template is spec-first; stakeholder constraints (Kotlin, single PTT, no keyboard) are explicit in the spec.
**Impact:** Greenfield work starts from contracts and monorepo skeleton per the plan; RN recommendation is not the delivery path unless the spec is updated.
**Owner:** Engineering + product confirmation on open assumptions (interrupt gesture, backend language pick, retention).

## [YYYY-MM-DD] [Short decision title]

**Context:** [What decision was needed and why]
**Options considered:** [Option A vs Option B and core tradeoff]
**Decision:** [Chosen option]
**Rationale:** [Why this option was selected]
**Impact:** [What changes as a result]
**Owner:** [Developer / Agent + developer confirmation]
