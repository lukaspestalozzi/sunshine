# Sunshine - Project Guidelines

## Project Overview

Sunshine is an Android app that shows users where the sun actually shines at any given time, considering terrain (mountains, hills). Target users are hikers in the Alps region. The app must work offline with previously downloaded data.

**Key documents:**
- `DESIGN.md` - Architecture, tech stack, and feature specifications

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM |
| DI | Koin |
| Maps | osmdroid (OpenStreetMap) |
| Database | Room + DataStore |
| Min SDK | API 29 (Android 10) |
| Build | Gradle with Kotlin DSL |

---

## Working Protocol

**Reality doesn't care about your model. The gap between model and reality is where all failures live.**

### The Explicit Reasoning Loop

**BEFORE every action that could fail** (build, test run, refactor, API call):

```
DOING: [action]
EXPECT: [specific predicted outcome]
IF PASS: [conclusion, next action]
IF FAIL: [conclusion, next action]
```

**AFTER the action:**

```
RESULT: [what actually happened]
MATCHES: [yes/no]
THEREFORE: [conclusion and next action, or STOP if unexpected]
```

Example:
```
DOING: ./gradlew testDebugUnitTest
EXPECT: All 12 tests pass, including new SunCalculatorTest
IF PASS: Mark task complete, move to UI implementation
IF FAIL: Check which test failed, investigate before proceeding

RESULT: 11 passed, 1 failed (calculateSunrise returns null for polar regions)
MATCHES: no
THEREFORE: STOP. Edge case not handled. Need to add polar region check.
```

### When Things Fail (Rule 0)

**On any failure: STOP. Explain before acting.**

1. State what failed (raw error, not interpretation)
2. State theory about why
3. State proposed action and expected outcome
4. Wait for confirmation on non-trivial fixes

Example:
```
FAILED: Room @Query compilation error
ERROR: "Not sure how to convert a Cursor to this method's return type"
THEORY: Return type Flow<List<Entity>> needs @Transaction or different query
PROPOSED: Add @Transaction annotation, expect compilation to succeed
```

Failure is information. Don't hide it, don't silently retry.

### Epistemic Hygiene

**Distinguish belief from verification:**
- "I believe the ViewModel handles null" = unverified, check the code
- "I verified null handling" = read the code, saw the `?: emptyList()`, have evidence

**"I don't know" is valid output.** Uncertainty expressed beats confident guessing.

**"Should" is a trap.** "This should compile but doesn't" means your model is wrong. Debug the model, not reality.

### Verification Checkpoints

**Batch size: 3 actions, then checkpoint.**

A checkpoint is **observable reality**:
- Run `./gradlew check`
- Run the specific test
- Build and run on emulator
- Read the actual Logcat output

TodoWrite is not a checkpoint. Planning is not a checkpoint. Reality is the checkpoint.

More than 5 code changes without verification = accumulating unjustified beliefs.

### Testing Protocol

**One test at a time. Run it. Watch it pass. Then next.**

Never:
- Write multiple tests before running any
- See failure and move to next test
- Skip tests you couldn't figure out

**Before marking any test complete:**
```
VERIFY: Ran `./gradlew test --tests "*.SunCalculatorTest"` — Result: PASS
```

If DID NOT RUN, cannot mark complete.

### Notice Confusion

**Surprise = your model is wrong in a specific way.**

When confused (unexpected Compose recomposition, Koin resolution failure, coroutine behavior):
- STOP—don't push past it
- Identify what belief turned out false
- Log it: "I assumed StateFlow would emit immediately, but it's conflated"

Confusion is signal, not noise.

### Investigation Protocol

When you don't understand a bug or behavior:

1. Create `investigations/[topic].md` if complex
2. Separate **FACTS** (verified) from **THEORIES** (plausible)
3. **Maintain 3+ competing theories**—never chase just one
4. Hypothesis before action; result after

Example investigation structure:
```markdown
# Investigation: Map not updating on time change

## FACTS (verified)
- TimeSlider emits new values (logged)
- ViewModel receives updates (logged)
- MapView.invalidate() is called

## THEORIES
1. osmdroid caching old tiles
2. Overlay not being redrawn
3. Wrong thread for UI update

## TESTS
| Test | Expected | Actual | Conclusion |
|------|----------|--------|------------|
| Force overlay.invalidate() | Redraw | No change | Not overlay issue |
```

### Autonomy Boundaries

**Ask before acting when:**
- Ambiguous requirements or intent
- Multiple valid architectural approaches
- Anything irreversible (schema migrations, API contracts)
- Scope change discovered mid-task
- "Not sure this is what's wanted"

**Autonomy check:**
```
- Confident this is the right approach? [yes/no]
- If wrong, blast radius? [low/medium/high]
- Easily undone? [yes/no]
- Should user know first? [yes/no]
```

Uncertainty + consequence → STOP, surface to user.

### When to Push Back

**Push back when:**
- Concrete evidence approach won't work
- Request contradicts stated project goals
- You see downstream effects not yet considered

**How:**
- State concern concretely with evidence
- Share what you know that might not be obvious
- Propose alternative if you have one
- Defer to user's decision

You're a collaborator, not a shell script.

---

## Key Disciplines

**Chesterton's Fence:** Explain why something exists before removing it. Can't explain why `@Transaction` is on that query? Don't remove it.

**Premature Abstraction:** Need 3 real examples before abstracting. Two similar `suspend fun`s don't justify a framework.

**Root Cause:** Ask why 5 times. The Compose crash appears in UI, but cause lives in ViewModel state update.

**One-way doors:** Pause before irreversible actions (Room migrations, published API changes). Design for undo.

**Fallbacks hide bugs:**
```kotlin
// BAD: Hides the real problem
val data = repository.getData() ?: emptyList()

// BETTER: Let it crash, crashes are data
val data = repository.getData()
    ?: throw IllegalStateException("Repository returned null unexpectedly")
```

**Git discipline:**
- `git add .` is forbidden. Add files individually.
- Know what you're committing.
- Review diff before commit.

---

## Coding Philosophy

Inspired by the Zen of Python, adapted for Kotlin/Android:

### Core Principles

1. **Explicit is better than implicit**
   - Avoid magic. Prefer explicit dependency injection over service locators.
   - Use named parameters for clarity when calling functions with multiple arguments.
   - Make nullability explicit with Kotlin's type system.

2. **Simple is better than complex**
   - Prefer straightforward implementations over clever ones.
   - A function should do one thing well.
   - If a class needs extensive comments to explain, it's too complex.

3. **Flat is better than nested**
   - Use early returns to avoid deep nesting.
   - Prefer `when` expressions over nested `if-else`.
   - Extract deeply nested logic into well-named functions.

4. **Readability counts**
   - Code is read far more often than it's written.
   - Use meaningful names: `calculateSunVisibility()` not `calc()`.
   - Keep functions short (aim for <20 lines).

5. **Errors should never pass silently**
   - Handle errors explicitly; don't swallow exceptions.
   - Use sealed classes for result types where appropriate.
   - Log errors with context for debugging.

6. **Practicality beats purity**
   - Don't over-engineer for hypothetical future requirements.
   - It's okay to use simple solutions for simple problems.
   - Refactor when patterns emerge, not before.

### Kotlin-Specific Guidelines

```kotlin
// DO: Use data classes for value types
data class GeoPoint(val latitude: Double, val longitude: Double)

// DO: Use sealed classes for restricted hierarchies
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

// DO: Use extension functions to add behavior without inheritance
fun GeoPoint.distanceTo(other: GeoPoint): Double { ... }

// DO: Prefer immutability
val items = listOf(1, 2, 3)  // not mutableListOf unless mutation is needed

// DO: Use named arguments for clarity
calculateVisibility(
    observer = point,
    dateTime = now,
    includeRefraction = true
)

// DON'T: Use !! operator - handle nulls explicitly
val name = user?.name ?: "Unknown"  // not user!!.name

// DON'T: Create deep inheritance hierarchies
// Prefer composition over inheritance
```

### Code Organization

```
// File structure within a feature
feature/
├── FeatureScreen.kt        # Composable UI
├── FeatureViewModel.kt     # State management
├── FeatureUiState.kt       # UI state data class
└── FeatureComponents.kt    # Reusable composables for this feature
```

---

## Testability Guidelines

### Design for Testability

1. **Constructor injection only**
   - All dependencies passed via constructor
   - No `lateinit` for dependencies
   - Makes faking/mocking trivial

2. **Pure functions where possible**
   - Given the same input, always return the same output
   - No side effects
   - Easy to test in isolation

3. **Separate concerns**
   - ViewModels don't know about Android framework classes
   - Use interfaces for external dependencies (network, database, sensors)
   - Domain logic has no Android dependencies

4. **Small, focused classes**
   - Each class has a single responsibility
   - Easier to test exhaustively
   - Easier to understand and maintain

### Test Structure

```kotlin
class SunCalculatorTest {
    // Arrange-Act-Assert pattern
    @Test
    fun `sun is below horizon at midnight`() {
        // Arrange
        val calculator = LocalSunCalculator()
        val midnight = LocalDateTime.of(2024, 6, 21, 0, 0)
        val location = GeoPoint(46.8182, 8.2275) // Swiss Alps

        // Act
        val position = calculator.calculateSunPosition(location, midnight)

        // Assert
        assertThat(position.elevationAngle).isLessThan(0.0)
    }
}
```

### Test Naming

Use descriptive test names with backticks:
- `fun \`returns empty list when no results found\`()`
- `fun \`throws exception when network unavailable\`()`
- `fun \`calculates correct sunrise for summer solstice\`()`

### What to Test

| Layer | What to Test | How |
|-------|--------------|-----|
| ViewModel | State transitions, error handling | Unit tests with fake repos |
| UseCase | Business logic, edge cases | Unit tests |
| Repository | Data mapping, caching logic | Integration tests with in-memory DB |
| UI | Critical user flows | Compose UI tests (sparingly) |

---

## CI Pipeline

### Local Testing (before push)

All CI checks must be runnable locally. Use the following commands:

```bash
# Run all checks (same as CI)
./gradlew check

# Individual checks
./gradlew ktlintCheck      # Code formatting
./gradlew detekt           # Static analysis
./gradlew testDebugUnitTest # Unit tests
./gradlew lintDebug        # Android lint

# Quick feedback loop during development
./gradlew testDebugUnitTest --continuous
```

### CI Workflow

```yaml
# .github/workflows/ci.yml runs:
1. ktlintCheck - Code style
2. detekt - Static analysis
3. lintDebug - Android-specific issues
4. testDebugUnitTest - Unit tests
5. assembleDebug - Build verification
```

### Pre-commit Checklist

Before committing, ensure:
- [ ] `./gradlew check` passes locally
- [ ] New code has tests
- [ ] No `// TODO` without issue reference
- [ ] No hardcoded strings (use resources)
- [ ] No suppressed warnings without justification

---

## Commit Messages

Follow conventional commits:

```
type(scope): description

feat(map): add time slider component
fix(elevation): handle API timeout gracefully
refactor(suncalc): extract interface for calculator
test(visibility): add edge cases for polar regions
docs(readme): update setup instructions
chore(deps): bump Compose to 1.5.0
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`

---

## Dependencies

### Adding Dependencies

1. Add to version catalog (`gradle/libs.versions.toml`)
2. Use in build.gradle.kts via catalog reference
3. Document why the dependency is needed in PR

### Avoiding Dependency Bloat

- Prefer standard library solutions
- Evaluate bundle size impact
- Check maintenance status (last commit, open issues)
- Avoid dependencies for trivial functionality

---

## Common Patterns

### ViewModel State

```kotlin
data class MapUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val data: MapData? = null
)

class MapViewModel(
    private val useCase: GetMapDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            useCase()
                .onSuccess { data ->
                    _uiState.update { it.copy(isLoading = false, data = data) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
}
```

### Repository Pattern

```kotlin
interface ElevationRepository {
    suspend fun getElevation(point: GeoPoint): Result<Double>
    suspend fun getElevationGrid(bounds: BoundingBox): Result<ElevationGrid>
}

class ElevationRepositoryImpl(
    private val localDataSource: ElevationLocalDataSource,
    private val remoteDataSource: ElevationRemoteDataSource
) : ElevationRepository {

    override suspend fun getElevation(point: GeoPoint): Result<Double> {
        // Offline-first: check cache, then network
        return localDataSource.getElevation(point)
            ?: remoteDataSource.getElevation(point)
                .also { result ->
                    result.onSuccess { localDataSource.cache(point, it) }
                }
    }
}
```

---

## Handoff Protocol

When stopping work (decision point, task complete, blocked):

1. **State of work:** done, in progress, untouched
2. **Current blockers:** why stopped, what's needed
3. **Open questions:** unresolved ambiguities, theories
4. **Recommendations:** what next and why
5. **Files touched:** created, modified, deleted

Leave the codebase clean for the next session.

---

## Resources

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [DESIGN.md](./DESIGN.md) - Project architecture and specifications
