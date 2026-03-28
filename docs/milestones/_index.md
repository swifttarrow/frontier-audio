# Milestones: Jarvis MVP

**Source plan:** [`../1-mvp.md`](../1-mvp.md)  
**Technical spec:** [`../../specs/1-initial.md`](../../specs/1-initial.md)

## Milestone Order

| # | Milestone | Status |
|---|-----------|--------|
| 0 | [m0-contracts-and-repo-skeleton](./m0-contracts-and-repo-skeleton/) | Pending |
| 1 | [m1-backend-skeleton](./m1-backend-skeleton/) | Pending |
| 2 | [m2-tools-github-operational-api](./m2-tools-github-operational-api/) | Pending |
| 3 | [m3-memory](./m3-memory/) | Pending |
| 4 | [m4-kotlin-android-client](./m4-kotlin-android-client/) | Pending |
| 5 | [m5-hardening-and-evaluation](./m5-hardening-and-evaluation/) | Pending |

## Quick Links

- [Plan](../1-mvp.md)
- [Technical spec](../../specs/1-initial.md)
- [PRD](../../prd.md)
- [Research](../../research/) — voice stack, GitHub contract, mobile audio, grounding

## Dependency chain

```text
m0 (contracts + skeleton)
 → m1 (WS + STT/TTS + DB)
   → m2 (tools + orchestrator)
     → m3 (memory)
       → m4 (Android client)  [can start m4 in parallel after m0 for UI shell, but E2E needs m1+]
         → m5 (hardening)
```

**Note:** Android work in m4 depends on **m0** WebSocket contract; full voice E2E requires **m1** online. Recommended: complete m0 → m1 → start m4 protocol tests against m1 while m2/m3 proceed.
