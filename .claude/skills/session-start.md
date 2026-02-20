# Session Start

## When to use

At the beginning of any new working session, before starting on tasks.

## Workflow

### 1. Review Lessons

Read `tasks/lessons.md` if it exists:
- Scan all entries for rules relevant to today's task
- Pay special attention to recent lessons (last 5 entries)
- Keep relevant rules in mind throughout the session

If the file doesn't exist, skip — don't create an empty one.

### 2. Review Open Tasks

Read `tasks/todo.md` if it exists:
- Check for incomplete items (`- [ ]`) from previous sessions
- Note any items that were blocked or need decisions
- Understand the state of in-progress work

If the file doesn't exist, skip.

### 3. Brief the User

Provide a concise summary:
- **Continuing**: any open items from last session
- **Lessons**: relevant rules that apply to today's work (if any)
- **Blockers**: decisions or information needed before starting

### 4. Ready to Work

After the briefing, proceed with the user's request using the full
workflow (plan-task for non-trivial work, etc.).

## Anti-Patterns

- Starting to code immediately without checking context
- Creating empty `tasks/lessons.md` or `tasks/todo.md` files
- Dumping the entire lessons file on the user — summarize what's relevant
- Skipping this step because it "wastes time"
