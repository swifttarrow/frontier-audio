# Research: Code Indexing Scope

## Question

How much actual source code should v1 index, and what code retrieval strategy is realistic for a trustworthy first release?

## System Understanding

- v1 needs to answer repository questions, but not every repository question needs deep code understanding.
- The current supported question set already includes file and string lookup.
- Code indexing is where "any public repo" most quickly becomes too broad.

## Hard Problems

- Source code retrieval has different needs from issue and PR retrieval.
- Symbols, filenames, and exact strings are often more important than semantic similarity.
- Generated files, vendored dependencies, and binary blobs poison retrieval quality.
- Chunking code without structure can dilute answer quality.

## Architecture Options

### Option A: No Code Indexing in v1

- Answer only from metadata, README, issues, PRs, comments, and commits.

Strengths:

- Lowest complexity.
- Strongest trust posture.

Weaknesses:

- Makes "what files mention X?" and similar questions impossible.
- Weakens repository usefulness for technical operators.

### Option B: Limited Code Lookup in v1

- Index code for exact string lookup, path lookup, and nearby snippets.
- Exclude broad architectural reasoning over the whole codebase.

Strengths:

- Supports concrete technical queries.
- Keeps scope manageable.

Weaknesses:

- Still requires file filtering and chunking discipline.
- Must clearly refuse broader design questions.

### Option C: Full Code Q&A in v1

- Index code and attempt broad codebase reasoning from day one.

Strengths:

- Most ambitious user story coverage.

Weaknesses:

- Highest hallucination and scope risk.
- Hard to support well across arbitrary public repos.

## Recommendation

Choose limited code lookup in v1.

### Supported Code Questions

- Exact string or symbol presence
- File discovery by term or path
- Nearby snippet retrieval
- Diff-localized PR questions when diffs are indexed

### Unsupported Code Questions

- Broad architecture opinions
- Hidden intent or rationale not stated in code or discussions
- Whole-codebase change impact claims without explicit evidence

## Indexing Design

- Index only the default branch for v1.
- Exclude binaries, vendored dependencies, generated artifacts, build outputs, and minified files.
- Chunk code by file with overlap, but preserve path, extension, and line span metadata.
- Keep code retrieval path-aware and keyword-heavy, with semantic retrieval as a supplement rather than the only mechanism.

## Deep Decisions

### Decision: Raw Text vs Syntax-Aware Indexing

Option A:
- Raw text chunks only.

Option B:
- Tree-sitter or syntax-aware symbol extraction.

Tradeoffs:
- Raw text is faster to ship.
- Syntax-aware indexing improves symbol lookup and snippet precision, but adds parser complexity.

Recommendation:
- Start with raw text chunks plus path metadata.
- Consider tree-sitter in the next iteration if symbol-heavy questions underperform.

### Decision: Default Branch Only vs Multi-Branch

Recommendation:
- Default branch only for v1.

Reasoning:
- Branch-aware indexing multiplies storage, freshness, and support-policy complexity.

## Initial Technology Picks

- Code indexing: included, but limited
- Parsing: text-first, no AST dependency in the first implementation
- Ranking bias: keyword > path match > semantic similarity for code questions

## Fast Experiments

1. Evaluate code lookup quality on symbol, filename, and stack-trace queries.
2. Compare text-only chunking with a prototype tree-sitter symbol table on a small benchmark.
3. Measure how much code indexing increases ingestion time for moderate repos.

## Sources

- [Understanding GitHub Code Search syntax (GitHub)](https://docs.github.com/en/search-github/github-code-search/understanding-github-code-search-syntax)
- [Navigating code on GitHub (GitHub)](https://docs.github.com/en/repositories/working-with-files/using-files/navigating-code-on-github)
