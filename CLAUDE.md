# Sunshine

Android app that shows where and when the sun actually shines, taking terrain occlusion into
account. Target users: hikers in the Alps. Offline-first.

**Status:** from-scratch rewrite, driven by OpenSpec. There is no app code yet; the first change
is `add-app-shell` (see `docs/roadmap.md`).

## Where things are

| Path | What |
|------|------|
| `openspec/config.yaml` | Project context and rules: constraints, decided stack, module boundary, quality bar. Read first. |
| `openspec/specs/` | Current truth: what the app does. Grows as changes are archived. |
| `openspec/changes/` | In-flight changes: proposal, spec deltas, design, tasks. |
| `docs/roadmap.md` | Ordered list of planned changes, their status, and legacy failure modes to avoid. |
| `docs/legacy-design.md` | Design of the discarded first implementation. Source material only. |
| `investigations/` | Verified reference values (test oracles). |
| `core/`, `app/` | Created by `add-app-shell`: pure Kotlin/JVM domain module; Android app module. |
| `scripts/` | Local CI simulation and proxy helpers (re-verified by `add-app-shell`). |
| `.claude/hooks/session-start.sh` | Installs the OpenSpec CLI and the Android SDK in web sessions. |

## Workflow: no code without an approved change

1. `/opsx:explore` (optional): think an idea through.
2. `/opsx:propose <change-name>`: creates `openspec/changes/<change-name>/` with proposal,
   spec deltas, design and tasks.
3. Decisions that shape the change are asked while proposing. The user reviews the whole change
   before it is applied.
4. `/opsx:apply`: implement the tasks in order; each task has its own verification step.
5. `/opsx:verify`: check the implementation against the change.
6. `/opsx:archive`: merge the spec deltas into `openspec/specs/`; update `docs/roadmap.md`.

Useful CLI: `openspec list`, `openspec show <name>`, `openspec validate --all --strict`
(also run by CI).

## Build and verify

Valid once `add-app-shell` has created the Gradle modules:

- `./scripts/verify-local.sh`: full CI simulation. Run before every push.
- `./scripts/verify-local.sh --quick`: ktlint only.
- `./scripts/run-with-proxy.sh <gradle task>`: single Gradle task through the proxy helper.
- CI (`.github/workflows/ci.yml`): `specs` job (OpenSpec validation) and `build` job (ktlint,
  Android lint, unit tests, debug APK).
- Web sessions: the SessionStart hook installs the SDK at `~/android-sdk` and writes
  `local.properties`.

## Conventions

- Conventional commits: `type(scope): description`. Types: feat, fix, refactor, test, docs,
  chore, style, ci.
- Stage files individually (never `git add .`); review the diff before committing.
- ktlint with zero issues and Android lint clean; no suppressions without a written
  justification. detekt is deferred until detekt 2.0 is stable.
- Numeric behavior is tested in `core` against reference values with explicit tolerances.
