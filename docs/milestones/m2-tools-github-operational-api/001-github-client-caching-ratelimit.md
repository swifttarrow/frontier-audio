# Spec 001: GitHub client — public repo reads, cache, rate limits

## Summary

Implement a **server-side** GitHub client that uses `IntegrationConfig.defaultPublicRepoUrl` to resolve **owner/repo**, fetches **issues, PRs, comments, merges** as needed for MVP demos, applies **HTTP caching** with TTL ≤ **3 minutes** policy, and handles **rate limit** responses with structured errors for the orchestrator.

**Plan:** [Phase 2](../../1-mvp.md#phase-2-tools--github-and-operational-api) · **Research:** `docs/research/2026-03-26-github-integration-contract.md`

## Scope

### In scope

- Parse `owner/repo` from configured HTTPS URL
- Authenticate with `GITHUB_TOKEN` (PAT) for higher rate limits; unauthenticated mode documented with lower limits
- Operations (minimum): `listOpenPullRequests(limit)`, `listOpenIssues(limit)`, `recentMergedPullRequests(limit)`, `getIssueComments(issueNumber)` — **finalize list** in code with OpenAPI-style docstring
- In-memory cache keyed by `(operation, params)` with configurable TTL default **180s**
- On HTTP 403 with rate limit headers or 429 → return `Result.failure(GitHubApiException(code="github.rate_limited", retryable=true, resetAt=...))`

### Out of scope

- Private repos
- GitHub App installation flow

## Dependencies

- **Prior specs:** [m1/002](../m1-backend-skeleton/002-integration-config-default-repo.md)
- **External:** `GITHUB_TOKEN` optional; GitHub REST API base `https://api.github.com`

## Interfaces & contracts

### Functions/modules

```text
interface GitHubClient {
  suspend fun listOpenPullRequests(limit: Int = 20): Result<GitHubPayload>
  suspend fun listOpenIssues(limit: Int = 20): Result<GitHubPayload>
  // ... others as finalized
}

data class GitHubPayload(
  val items: List<Map<String, Any>>,
  val asOf: Instant,
  val etag: String?
)
```

- **Raw JSON** preserved for LLM grounding (or normalized maps); document which

### Data & config

| Env | Purpose |
|-----|---------|
| `GITHUB_TOKEN` | PAT |
| `GITHUB_CACHE_TTL_SECONDS` | default 180 |

## Behavior

### Acceptance criteria

1. All responses include `asOf` instant set when data was fetched.
2. Second identical call within TTL does **not** hit network (test with mock server call count).
3. Rate-limited response surfaces `retryable=true` and human-readable `message` for TTS.

### Edge cases & errors

- Unknown repo → `github.not_found`, `retryable: false`
- Empty list is valid; orchestrator must not invent items

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../github/GitHubClient.kt` | HTTP + cache |
| Create | `services/voice-gateway/src/.../github/GitHubExceptions.kt` | Typed errors |

## Verification

### Automated

- [ ] Mock HTTP: 200 with body, 304 with etag, 429 rate limit
- [ ] Cache hit test

### Manual

- [ ] Run against a small public fixture repo; logs show `asOf`

## Notes

- Respect GitHub **conditional requests** with ETag when feasible to save quota.
