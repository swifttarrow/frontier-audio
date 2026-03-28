# Spec 004: Evaluation scenarios document and root README

## Summary

Author **`docs/eval/mvp-scenarios.md`** listing **testable** scenarios mapped to PRD / spec IDs (voice status Q, GitHub PR list, memory recall, interrupt, self-description, unsupported request, empty API/GitHub, **~3 min** freshness). Update **root `README.md`** with prerequisites, how to run **docker compose**, **voice-gateway**, **Android app**, env var table, and evaluator notes (hosted vs local).

**Plan:** [Phase 5](../../1-mvp.md#phase-5-hardening-and-evaluation)

## Scope

### In scope

- Scenario template: **Given** setup, **When** user says X, **Then** expect Y (grounded behavior)
- Link each scenario to `docs/prd.md` criteria IDs where possible
- README sections: Architecture one-liner, Quickstart, Configuration, Testing, Troubleshooting (emulator networking)

### Out of scope

- Automated voice regression harness (optional future)

## Dependencies

- **Prior specs:** Functional completion m1–m4
- **External:** None

## Interfaces & contracts

### Docs

- `docs/eval/mvp-scenarios.md` — at least **12** scenarios covering plan Phase 5 manual checklist

## Behavior

### Acceptance criteria

1. New engineer can run stack from README without asking team.
2. Eval doc includes **negative** cases (no fabrication, rate limit).
3. Freshness scenario references how to force stale cache in m2 GitHub client tests.

### Edge cases & errors

- Document “known limitations” (single repo config, English only, etc.)

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `docs/eval/mvp-scenarios.md` | Checklist |
| Modify | `README.md` | Quickstart |

## Verification

### Automated

- [ ] N/A

### Manual

- [ ] Two people: one follows README cold, one runs eval doc

## Notes

- Keep URLs in README as examples; use placeholders for secrets.
