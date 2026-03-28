You are a senior software architect and database designer.

Generate a **full production-oriented relational database schema** from the **technical specification** for this project.

## Source of truth

1. **Primary:** the **tech spec** (technical specification). It defines entities, keys, retention, schema strategy, APIs that imply persistence, auth/session model, and open questions that affect storage.
2. **Secondary:** the **PRD** or other product docs **only when** the tech spec points to them (e.g. “derived from PRD §…”), defers a decision to product/legal, or leaves a gap you must not invent without that context.

Assume:
- a tech spec already exists in the repo/workspace **or** is pasted with the task
- you should read and use it as the **authoritative** input for what to persist and how
- if multiple spec-like files exist, prefer the one that matches the **current implementation scope** (filename, header scope notes, or recency) and state which file you used and why

Typical locations (not exhaustive): `docs/specs/*.md`, milestone docs under `docs/milestones/` that lock schema decisions, or an output produced from `agent/prompts/tech-spec.md`.

## Goal

Design a relational schema (implementation-ready DDL and documentation) that **implements the tech spec’s data model** and any persistence implied elsewhere in the spec (workflows, idempotency, audit, config, rate-limit bookkeeping, etc.). Stay aligned with the spec’s **Assumption** / **Open question** labels: prefer explicit columns or TBD notes over silent invention.

## What to do first

1. Find and read the tech spec (and PRD only as needed per “Source of truth” above).
2. Extract from the spec:
   - **§ Data model** (or equivalent): entities, relationships, identity keys, retention/deletion.
   - **APIs / interfaces**: resources that need tables, idempotency keys, correlation IDs, ordering constraints.
   - **AuthN / AuthZ**: session tokens, device binding, future user migration hooks if mentioned.
   - **NFRs**: observability or compliance that imply audit or metadata tables.
3. List ambiguities that **materially** affect tables, keys, or constraints.
4. State recommendations and assumptions where the spec is silent on DB details.
5. Then generate the full schema.

## Output format

Produce your response in the following sections:

1. **Tech spec source used**
   - file path(s); note any PRD or other docs consulted and why
   - brief summary of persistence-relevant decisions you extracted

2. **Schema assumptions**
   - important assumptions grounded in the spec
   - ambiguities and how you resolved them (or mark **TBD** for product/legal/engineering)

3. **Entity list**
   - concise list of core tables/entities and their role

4. **Relational schema**
   For each table include:
   - table name
   - purpose
   - columns
   - data types
   - nullability
   - default values
   - primary key
   - foreign keys
   - unique constraints
   - check constraints
   - indexes

5. **Relationship summary**
   - one-to-one, one-to-many, many-to-many as applicable

6. **Recommended enums / lookup tables**
   - DB enums vs lookup tables vs constrained text (justify per the spec’s evolution and query patterns)

7. **Audit / history strategy**
   - auditability, append-only or snapshot patterns **if** the spec requires them; otherwise state “not required by spec” explicitly

8. **Soft delete / archival strategy**
   - align with spec retention/deletion; where soft delete helps vs hard delete + tombstones

9. **Permission-sensitive data notes**
   - schema implications of auth, device scope, secrets, PII, or moderation if the spec includes them

10. **Migration order**
    - safe order for creating tables (respect FKs and extensions)

11. **SQL DDL**
    - PostgreSQL-flavored `CREATE TABLE` (and `CREATE TYPE` for enums if used)
    - indexes and constraints as above

12. **Spec traceability**
    - short mapping: table/group of tables → tech spec section(s) or bullet they satisfy

13. **Future-proofing notes**
    - what to keep flexible for later phases **as described in the spec** (e.g. adding user accounts without breaking device-scoped keys)

## Design constraints

Apply these **unless the tech spec explicitly chooses otherwise**:

- PostgreSQL
- UUID primary keys where the spec uses UUIDs or where surrogate keys are appropriate
- `timestamptz` for instants
- normalized core entities; explicit join tables for many-to-many
- lifecycle fields (`created_at`, `updated_at`, status) where entities have a lifecycle in the spec
- enforce in the database what must hold for **data integrity**; defer to the app what the spec marks as product/UI-only

Derive **feature-specific** requirements (sessions, turns, memory, tools, billing, etc.) **only** from the tech spec and cited product docs—not from a generic template.

## Important instructions

- Do **not** invent major persisted features **absent** from the tech spec (or clearly traced PRD scope the spec adopts).
- Do **not** over-optimize for speculative scale.
- Do **not** collapse distinct concepts just to reduce table count.
- If a rule is better enforced in the app than the DB, say so; if split enforcement is right, explain both.
- For major modeling choices, explain **why** in terms of the spec.

## Design questions to answer

Address the questions **raised by this tech spec** (not a fixed universal list). At minimum, scan the spec and answer any that apply, for example:

- Identity: surrogate UUID vs natural keys; device/session/user scoping and migration path.
- Idempotency and deduplication: columns and unique constraints for client-generated IDs or retries.
- Retention: TTL-friendly columns, partition hints, or separate archive tables if the spec discusses deletion.
- Large or binary payloads: in-row vs object storage references; what the spec says about audio or blobs.
- Config vs data: what belongs in env/config vs relational tables per the spec.
- Concurrency: versioning or optimistic locking if updates race on the same row.

If the spec lists **Open questions**, reflect them in schema assumptions or TBDs instead of pretending they are decided.

## Final review pass

After generating the schema, second-pass review:

1. Normalization issues  
2. Likely performance bottlenecks and missing indexes  
3. Constraints that belong in DB vs app  
4. Enums vs text vs lookup tables  
5. Tables that are premature for the spec’s stated phase  
6. Remaining spec or product ambiguities that still need clarification  
