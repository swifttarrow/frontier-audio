# Research: GitHub Integration Contract

## Question

How should the system talk to GitHub for a mobile GitHub Q&A assistant that accepts any public repository URL?

## System Understanding

- The product accepts any public GitHub repository URL as input.
- v1 needs repository metadata, issues, pull requests, comments, merges, commits, and possibly limited code lookup.
- The system must stay trustworthy under rate limits and partial support conditions.
- The contract must support both live API access and background indexing.

## Hard Problems

- Rate limits vary sharply between unauthenticated and authenticated access.
- Search endpoints are more restrictive than general REST access.
- GraphQL is powerful for nested data but has point, node, and timeout constraints.
- A GitHub App installation token only works for installed repositories, which conflicts with arbitrary public-repo access.
- API-only retrieval is not sufficient for robust hybrid search across repo text.

## Architecture Options

### Option A: REST-First

- Use REST for repository metadata, issues, pull requests, comments, commits, and file contents.
- Avoid GraphQL except where REST is clearly awkward.

Strengths:

- Easier to understand and debug.
- Stable endpoint-by-endpoint behavior.
- Good fit for background ingestion jobs.

Weaknesses:

- Nested traversal can require many requests.
- Harder to batch complex data shapes efficiently.

### Option B: GraphQL-Heavy

- Use GraphQL for most repository graph traversal.
- Use REST only for gaps and search-specific endpoints.

Strengths:

- Flexible graph traversal.
- Cleaner fetches for connected issue and PR data.

Weaknesses:

- Query-cost budgeting is non-trivial.
- Node limits and timeouts punish broad queries.
- More work to tune and monitor.

### Option C: Hybrid API + Repository Fetch

- Use REST and selective GraphQL for metadata and discussions.
- Fetch repository contents through archive download or git clone for code and docs indexing.
- Build your own index rather than depending on GitHub search as the main retrieval engine.

Strengths:

- Best fit for hybrid retrieval.
- Less dependent on GitHub search quotas.
- Strongest path to deterministic support-status checks.

Weaknesses:

- Requires your own ingestion pipeline and storage.
- More backend work up front.

## Deep Decisions

### Decision: Authentication Model

Option A:
- Unauthenticated public requests.

Option B:
- Server-managed authenticated token for public data.

Option C:
- GitHub App installation auth.

Tradeoffs:
- Unauthenticated access is too rate-limited for production.
- GitHub App installation auth is excellent for installed repos but does not solve arbitrary public-repo access by itself.
- A server-managed authenticated token is the simplest v1 path for public-only access.

Recommendation:
- Start with server-side authenticated access for public data, with clear rate-limit monitoring.
- Treat GitHub App installation auth as a future path when private repos or org installs become relevant.

### Decision: REST vs GraphQL

Option A:
- Mostly REST.

Option B:
- Mostly GraphQL.

Option C:
- Hybrid.

Tradeoffs:
- REST is easier for ingestion workers.
- GraphQL is efficient for connected shapes but easier to over-query.
- Hybrid lets each API do what it is best at.

Recommendation:
- Use REST as the default ingestion API and GraphQL selectively for high-value connected fetches.

### Decision: GitHub Search as Source of Truth vs Helper

Option A:
- Depend on GitHub search for live answers.

Option B:
- Use GitHub search only as a fallback or bootstrap tool.

Tradeoffs:
- Depending on GitHub search makes support quality dependent on external search behavior and quotas.
- Using it as a helper keeps your own retrieval contract stable.

Recommendation:
- Build your own index and treat GitHub search as a fallback or sanity-check tool, not the primary retrieval engine.

## Recommendation

The initial GitHub integration contract should be:

- Server-side authenticated access for public repo metadata and discussion data.
- REST-first ingestion with selective GraphQL for nested or connected fetches.
- Repository contents fetched into your own indexing pipeline.
- GitHub search used only when it materially reduces work and does not become a hidden dependency.

## Initial Technology Picks

- Backend language: TypeScript
- GitHub SDK: Octokit
- Default data APIs: REST first, GraphQL second
- Repo content fetch: tarball or shallow clone of default branch
- Auth stance for v1: server-managed authenticated token for public data

## Fast Experiments

1. Measure request volume for ingesting a moderate repository with README, issues, pull requests, comments, and recent commits.
2. Compare REST-only vs hybrid REST+GraphQL request counts for a PR-focused question set.
3. Load-test a small public-repo ingestion queue against GitHub secondary limits.

## Sources

- [Rate limits for the REST API (GitHub)](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
- [Rate limits and query limits for the GraphQL API (GitHub)](https://docs.github.com/en/graphql/overview/rate-limits-and-query-limits-for-the-graphql-api)
- [Authenticating as a GitHub App installation (GitHub)](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation)
- [Understanding GitHub Code Search syntax (GitHub)](https://docs.github.com/en/search-github/github-code-search/understanding-github-code-search-syntax)
