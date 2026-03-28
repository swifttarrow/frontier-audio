# Research: Retrieval Architecture

## Question

What retrieval stack should back a mobile GitHub Q&A assistant with hybrid semantic and keyword search requirements?

## System Understanding

- The PRD already commits the product to hybrid semantic plus keyword retrieval.
- Answers must be grounded in explicit retrieved evidence, not loose synthesis.
- v1 does not need internet-scale search, but it does need predictable repo-scoped search.

## Hard Problems

- Retrieval must work across very different artifact types: README, issues, comments, diffs, commits, and code.
- Keyword matches remain critical for symbols, filenames, issue numbers, and error strings.
- Semantic matches are critical for paraphrased issue descriptions and natural-language queries.
- Ranking must stay explainable enough to support trust and debugging.

## Architecture Options

### Option A: Postgres-Centered Hybrid Search

- Store artifacts in Postgres.
- Use Postgres full-text search for keyword retrieval.
- Use `pgvector` for embeddings-based retrieval.
- Fuse the candidate sets with Reciprocal Rank Fusion or a simple weighted scorer.

Strengths:

- One primary datastore.
- Operationally simple for an MVP.
- Strong enough for repo-scoped search.

Weaknesses:

- Search tuning is your responsibility.
- Very large-scale search may outgrow it later.

### Option B: Split Search Stack

- Postgres for metadata and core records.
- Dedicated vector DB for embeddings.
- Dedicated text search engine for keyword ranking.

Strengths:

- Best specialization per subsystem.
- Easier to scale independently.

Weaknesses:

- Highest operational complexity.
- Harder to ship quickly.

### Option C: Live GitHub Search + Thin Cache

- Use GitHub search for keyword search.
- Add embeddings over a small local cache.

Strengths:

- Minimal indexing effort.

Weaknesses:

- External dependency becomes the search engine.
- Harder to guarantee support quality.
- Poor fit for consistent grounding and latency.

## Recommendation

Use a Postgres-centered hybrid stack for v1.

### Core Design

- One `documents` table for normalized search artifacts.
- One `document_chunks` table for chunked searchable content.
- `tsvector` columns for keyword search.
- `vector` columns via `pgvector` for semantic search.
- Rank fusion across keyword and vector candidates.
- Per-artifact metadata filters: repo, branch, doc type, issue number, PR number, path, author, timestamp.

## Artifact Strategy

- Repository metadata: single documents
- README and docs: chunked text documents
- Issues and PRs: body plus comment chunks
- Commits: message-level documents
- Diffs: chunked patch documents
- Code: chunked source files with path metadata

## Deep Decisions

### Decision: One Store vs Many

Recommendation:
- One primary Postgres store for v1.

Reasoning:
- The main risk is product ambiguity, not search scale.
- Postgres full-text search and `pgvector` are enough to validate the retrieval contract.

### Decision: Chunking Strategy

Option A:
- Fixed-size chunks.

Option B:
- Structure-aware chunking by artifact type.

Recommendation:
- Use structure-aware chunking where available and fixed-size fallback for raw code.

### Decision: Ranking Strategy

Option A:
- Weighted score merge.

Option B:
- Reciprocal Rank Fusion.

Option C:
- Cross-encoder reranker.

Recommendation:
- Start with Reciprocal Rank Fusion.
- Add reranking only if evaluation shows clear gaps.

## Initial Technology Picks

- Primary database: PostgreSQL
- Vector extension: `pgvector`
- Keyword search: PostgreSQL full-text search
- Fusion method: Reciprocal Rank Fusion
- Backend language: TypeScript

## What to Build First

1. Normalized document schema
2. Keyword search over README, issues, PRs, comments, and commits
3. Embeddings on the same artifacts
4. Hybrid fusion and artifact filters
5. Limited code chunks last

## Fast Experiments

1. Build a small relevance set of 100 repo questions and compare keyword-only, vector-only, and fused retrieval.
2. Measure whether code chunks materially improve answer quality for the first question set.
3. Test whether reranking is needed before adding another service.

## Sources

- [pgvector](https://github.com/pgvector/pgvector)
- [PostgreSQL full text search introduction](https://www.postgresql.org/docs/current/textsearch-intro.html)
