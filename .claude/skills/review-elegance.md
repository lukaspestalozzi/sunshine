# Review Elegance

## When to use

Before presenting any **non-trivial** change to the user. Non-trivial means: new feature, refactor, bug fix touching 3+ files, or anything you iterated on.

**Skip this for**: one-line fixes, simple renames, config changes, obvious corrections.

## Workflow

### 1. Pause Before Presenting

After implementing but before marking the task done, stop and reflect.

### 2. Ask Three Questions

1. **"Is there a more elegant way?"**
   - Look at the full diff. Does the solution feel clean and minimal?
   - Are there unnecessary layers, abstractions, or indirections?

2. **"Would a staff engineer approve this?"**
   - Is the code clear without extensive comments?
   - Does it follow existing patterns in the codebase?
   - Is the test coverage appropriate?

3. **"Knowing everything I know now, would I write it this way from scratch?"**
   - If you discovered things during implementation that would change your approach, consider rewriting.
   - The sunk cost of the current implementation is zero — it's just text.

### 3. If the Answer Is No

- Identify specifically what feels hacky or overly complex
- Implement the elegant solution instead
- Don't present the hacky version "for now" — do it right the first time

### 4. If the Answer Is Yes

Proceed. Don't over-iterate.

## Guardrails Against Over-Engineering

- "Elegant" means **clear, minimal, correct** — not clever or abstract
- Simple, obvious fixes don't need this step
- If you've been iterating for 3+ rounds, the current solution is probably fine
- Don't add abstractions for hypothetical future needs
- Don't refactor surrounding code just because you're in the neighborhood
- The goal is quality, not perfection

## Anti-Patterns

- Presenting code you know is hacky "to save time"
- Gold-plating simple changes with unnecessary elegance reviews
- Using this as an excuse to over-engineer or add features
- Iterating endlessly without converging
