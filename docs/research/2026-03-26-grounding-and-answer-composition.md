# Research: Grounding and Answer Composition

## Question

How should the system enforce the "direct quote or close restatement" rule without making answers robotic or unverifiable?

## System Understanding

- Trust is the product.
- The answer policy must be strict enough to prevent overclaiming and still usable in speech.
- Voice answers should be short, but every factual statement still needs evidence.

## Hard Problems

- Spoken answers cannot dump raw citations the way a text UI can.
- Strict extractive-only answers can sound unnatural.
- Loose paraphrase can quietly turn into unsupported synthesis.
- Multi-snippet answers are necessary for many real repo questions.

## Policy Options

### Option A: Fully Extractive

- Every answer is a direct quote or near-direct excerpt.

Strengths:

- Easiest policy to audit.

Weaknesses:

- Sounds stiff in voice.
- Poor for counts, comparisons, or concise summaries of multiple comments.

### Option B: Claim-Mapped Restatement

- The model may restate retrieved content in simpler spoken language.
- Every sentence must map to one or more specific evidence spans.

Strengths:

- Best balance of usability and auditability.
- Supports concise spoken answers.

Weaknesses:

- Requires structured answer generation and validation.

### Option C: Standard RAG Summary

- The model summarizes retrieved snippets more freely.

Strengths:

- Most natural-sounding answers.

Weaknesses:

- Too risky for this product stance.

## Recommendation

Use claim-mapped restatement.

### Allowed

- Pronoun resolution when obvious
- Tense normalization
- Converting issue text into short spoken phrasing
- Combining multiple retrieved snippets when each claim has evidence

### Not Allowed

- Speculating about intent
- Explaining unstated architectural rationale
- Inferring future behavior from partial evidence
- Collapsing multiple comments into a broad summary without evidence mapping

## Proposed Answer Pipeline

1. Retrieval returns candidate snippets.
2. Answer planner selects the minimum evidence needed.
3. Composer returns structured JSON:
   - `answer_text`
   - `claims`
   - `evidence_refs`
   - `support_status`
4. Validator rejects any claim without evidence.
5. TTS speaks only the validated `answer_text`.

## Evaluation Harness

The research outcome for this topic should include:

- supported question set
- unsupported question set
- ambiguous question set
- claim-to-evidence validation checks
- refusal correctness checks
- latency measurements per answer stage

## Technology Direction

- Use a text model that can reliably produce structured JSON with evidence references.
- Keep answer composition on the server, not embedded inside a speech-to-speech black box.
- Preserve transcript, evidence IDs, and refusal reason for offline evaluation.

If using OpenAI for the v1 chain, a strong starting shape is:

- STT: `gpt-4o-transcribe`
- Answer model: `gpt-4.1` class text model with structured output
- TTS: `gpt-4o-mini-tts`

## Fast Experiments

1. Create 50 questions and score extractive-only vs claim-mapped restatement on clarity and correctness.
2. Build a validator that hard-fails any answer sentence without evidence coverage.
3. Compare spoken brevity with and without explicit source framing.

## Sources

- [Voice agents (OpenAI)](https://platform.openai.com/docs/guides/voice-agents)
