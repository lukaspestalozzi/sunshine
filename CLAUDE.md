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

## Resources

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [DESIGN.md](./DESIGN.md) - Project architecture and specifications
