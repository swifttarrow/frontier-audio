# Research: Public Repository Support Policy

## Question

What support policy should v1 use if users may submit any public GitHub repository URL, but not every repository can be indexed or answered equally well?

## System Understanding

- Product trust depends on saying what is supported before pretending to understand a repository.
- Public-repo input should stay open, but the answer contract must reflect real indexing limits.
- v1 does not need to support every repository equally to feel credible.

## Hard Problems

- Repository health depends on size, structure, content mix, and change rate, not just one metric.
- Binary-heavy and generated-code-heavy repos are poor fits for text retrieval.
- Repo size alone does not predict indexing time; file count and document quality matter.
- Users will interpret "any repo" as "fully supported" unless the product says otherwise.

## Architecture Options

### Option A: Binary Support Model

- Supported or unsupported only.

Strengths:

- Simple to explain.
- Easy to implement.

Weaknesses:

- Too blunt for real repositories.
- Throws away useful partial answers.

### Option B: Tiered Support Model

- Fully supported, partially supported, unsupported.

Strengths:

- Best user-trust model.
- Matches real indexing outcomes.
- Allows README/issues/PRs-only fallback.

Weaknesses:

- Requires careful UX wording.
- Needs support classification logic.

### Option C: Silent Best-Effort Model

- Always try to answer from whatever is available without exposing support status.

Strengths:

- Minimal UX overhead.

Weaknesses:

- High trust risk.
- Encourages accidental overclaiming.

## Recommendation

Use a tiered support model.

### Fully Supported

- Repo contents, metadata, issues, PRs, comments, recent commits, and selected code are indexed successfully within v1 limits.
- Broad repository questions and scoped code-lookup questions are allowed.

### Partially Supported

- Metadata and discussion artifacts are indexed, but full code indexing is skipped, incomplete, or too slow.
- The system may answer README, issue, PR, comment, and commit questions.
- The system should refuse broad codebase questions.

### Unsupported

- The repo is too large, too binary-heavy, too sparse in retrievable text, or otherwise exceeds v1 limits.
- The system should explain the reason and refuse broad repository Q&A.

## Thresholds Worth Testing

These should be validated with a benchmark set, but they are a reasonable starting point:

- Full support target:
  - default branch archive or clone under 500 MB compressed equivalent
  - fewer than 25,000 candidate text files after exclusions
  - indexing ready in under 60 seconds on the standard ingest worker
- Partial support target:
  - metadata and discussions ingest cleanly
  - code indexing skipped after a time or size threshold
- Unsupported triggers:
  - more than 100,000 files
  - heavy LFS or binary content
  - indexing exceeds the hard budget

## Signals the Classifier Should Use

- Repository size
- Estimated file count
- Text vs binary ratio
- Default branch archive size
- Vendor and generated directory prevalence
- Presence of LFS pointers
- Historical indexing time

## Initial Delivery Recommendation

Build a repository support classifier before answer generation. It should run:

- once at repo submission time
- again after ingestion completes
- whenever a user asks a question that needs artifacts not yet indexed

The classifier output should be stored and shown in the mobile UI.

## Initial Technology Picks

- Classifier home: backend ingestion service
- Storage: Postgres support-status table keyed by owner/repo and indexed revision
- UX copy: explicit badges for full, partial, unsupported

## Fast Experiments

1. Build a benchmark corpus of 25 public repos across small, medium, huge, binary-heavy, and monorepo shapes.
2. Measure ingest time, text yield, and answer quality under increasingly strict thresholds.
3. Tune fallback messaging with sample user prompts to ensure refusals still feel helpful.

## Sources

- [About large files on GitHub (GitHub)](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)
- [Navigating code on GitHub (GitHub)](https://docs.github.com/en/repositories/working-with-files/using-files/navigating-code-on-github)
- [Repository limits (GitHub Enterprise Server)](https://docs.github.com/en/enterprise-server%403.15/repositories/creating-and-managing-repositories/repository-limits)
