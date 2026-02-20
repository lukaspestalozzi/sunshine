# Plan Task

## When to use

Use this workflow for ANY non-trivial task (3+ steps or architectural decisions). If in doubt, plan. The cost of planning is low; the cost of re-doing is high.

## Workflow

### 1. Assess Complexity

Before coding, classify the task:
- **Trivial** (1-2 steps, single file, obvious fix) → just do it
- **Non-trivial** (3+ steps, multiple files, design decisions) → plan first

### 2. Write Plan to `tasks/todo.md`

Create or update `tasks/todo.md` with:

```markdown
# Task: [short title]
Date: [date]
Branch: [branch name]

## Plan
- [ ] Step 1 → verify: [how to verify]
- [ ] Step 2 → verify: [how to verify]
- [ ] Step 3 → verify: [how to verify]

## Review
[Filled on completion]
```

Rules:
- Every step has an explicit verification method
- Include verification steps as their own items, not afterthoughts
- Write detailed enough specs to reduce ambiguity
- Use plan mode for verification strategy, not just building

### 3. Get Confirmation

Present the plan to the user before starting. Don't silently begin.

### 4. Execute and Track

- Mark items `[x]` in `tasks/todo.md` as you complete them
- Also use the TodoWrite tool for real-time UI progress
- Explain changes at each step with a high-level summary

### 5. On Failure — STOP and Re-Plan

If something goes sideways:
- **Do NOT keep pushing** through the original plan
- Update `tasks/todo.md` with what failed and why
- Add a `## Re-Plan` section with the revised approach
- Get re-approval before continuing

### 6. Complete

Fill in the `## Review` section with:
- What was done (summary of changes)
- What was verified (tests, builds, manual checks)
- Any open items or follow-ups
- Files touched (created, modified, deleted)

## Anti-Patterns

- Writing code before having a plan for non-trivial work
- Planning in your head instead of in `tasks/todo.md`
- Continuing a failing plan instead of stopping to re-plan
- Marking steps complete without verification
