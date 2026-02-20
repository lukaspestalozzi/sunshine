# Capture Lesson

## When to use

- After ANY correction from the user (code, process, approach, communication)
- When you discover something surprising during implementation
- When a theory turns out wrong in an interesting way
- When you make a mistake, even if the user doesn't catch it

## Core Principle

**Ruthlessly iterate on lessons until mistake rate drops.** Every correction is a gift — extract a concrete rule that prevents recurrence.

## Workflow

### 1. Identify the Mistake

Be specific. Not "I made a bad assumption" but "I assumed Room DAOs return non-null by default, but they return nullable when using @Query with LEFT JOIN."

### 2. Write the Rule

Update `tasks/lessons.md` with a new entry:

```markdown
### [Short descriptive title]
- **Context**: [What situation triggered the mistake]
- **Mistake**: [What you did wrong, specifically]
- **Rule**: [Concrete, actionable rule that prevents recurrence]
- **Date**: [When learned]
```

### 3. Make It Actionable

The **Rule** field must be:
- Specific enough to follow mechanically
- Phrased as a positive instruction ("Always check X") not just a negative ("Don't do Y")
- Scoped to the right level — not so broad it's useless, not so narrow it only applies once

Good: "Before writing Room @Query return types, check if the query uses JOINs — if so, make the return type nullable."
Bad: "Be more careful with Room."

### 4. Review Cycle

- Read `tasks/lessons.md` at the start of each session
- When working on a related area, actively recall relevant lessons
- If a lesson keeps getting violated, strengthen the rule or make it more prominent

## Examples

### Don't guess at API response shape
- **Context**: Implementing elevation API parsing
- **Mistake**: Assumed `{elevation: number}`, actual format was `{results: [{elevation: number}]}`
- **Rule**: Always read existing parsing code or API docs before writing new response handling. Never assume response shape.
- **Date**: 2026-02-20

### Match existing code style over personal preference
- **Context**: Adding a new ViewModel
- **Mistake**: Used `sealed interface` for UI state when the project consistently uses `data class` with nullable fields
- **Rule**: Before writing new code in an existing pattern (ViewModel, Repository, etc.), read 2+ existing examples and match their style exactly.
- **Date**: 2026-02-20
