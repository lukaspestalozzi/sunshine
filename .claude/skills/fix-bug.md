# Fix Bug

## When to use

When given a bug report, failing test, CI failure, or error log. Also use proactively when you notice something broken during other work.

## Core Principle

**Just fix it. Don't ask for hand-holding.** Zero context switching required from the user. Point at logs, errors, failing tests — then resolve them.

## Workflow

### 1. Investigate (Don't Ask)

- Read the error, log, or failing test output
- Trace the root cause through the code
- Use subagents for parallel exploration if the codebase is large
- Form 3+ theories about the cause

### 2. Diagnose

- Narrow to root cause via code reading, not guessing
- Document: **FACT** (verified) vs **THEORY** (plausible)
- If complex (30+ min investigation), create `investigations/[topic].md`
- Test theories systematically — don't just try the first one

### 3. Fix

- Implement the **minimal fix** that addresses the root cause
- No temporary workarounds — find the real cause
- No unrelated cleanup mixed into the fix
- If the root cause is deeper than expected, flag scope to the user but still fix it

### 4. Verify

- Run the specific failing test/build
- Confirm the fix resolves the original issue
- Check that no other tests broke
- For CI failures: run `./scripts/verify-local.sh` — the full pipeline, not just the broken step

### 5. Report

Summarize concisely:
- **What broke**: the symptom
- **Why**: the root cause
- **Fix**: what you changed
- **Verified**: how you confirmed it works

## CI Failures

For failing CI specifically:
1. Run `./scripts/verify-local.sh` and read the full output
2. Fix ALL failures, not just the first one
3. Re-run until green
4. Don't stop at "it compiles" — run the full 5-step pipeline

## Anti-Patterns

- Asking the user "what should I do about this error?"
- Applying a band-aid without understanding root cause
- Fixing one test and not checking if others broke
- Waiting to be told to fix CI — just go fix it
