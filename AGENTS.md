# AGENTS.md — kotlinx-knit

## What This Project Is

**kotlinx-knit** is a Gradle plugin and CLI tool that generates and verifies Kotlin source example files and JUnit tests from markdown documentation. It ensures that code in docs always compiles and produces the expected output.

Primary workflows:
- `./gradlew knit` — generate/update example `.kt` files and test classes from `.md` files
- `./gradlew knitCheck` — verify those files are up-to-date (used in CI; `check` depends on this)

## Repository Layout

```
src/                        # Main plugin source (7 files, ~1300 LOC)
  KnitPlugin.kt             # Gradle plugin entry point + task registration
  Knit.kt                   # Core processing logic (~710 lines)
  KnitContext.kt            # Shared state across file processing
  KnitProps.kt              # Hierarchical property resolution
  KnitUtil.kt               # File I/O + line separator handling
  KnitApiParser.kt          # Parses paths-index.json from Dokka
  KnitLog.kt                # Logging abstraction (console vs Gradle logger)

test/                       # Plugin integration tests
  TestDataTest.kt           # Auto-discovers testdata/ fixtures and runs them
  TestDataGen.kt            # Generates test methods from testdata/ entries
  verifyTestData.kt         # Core test harness
  KnitTestProject.kt        # Temp-dir-based test project setup
  TestCaseReader.kt         # Parses .in.md and expected output files

kotlinx-knit-test/          # Published helper library for generated tests
  src/KnitTest.kt           # captureOutput(), verifyOutputLines(), etc.
  src/KnitDiff.kt           # Unified-diff implementation

pathsaver/                  # Dokka plugin that produces paths-index.json
  src/.../PathsaverPlugin.kt

buildSrc/                   # Build helpers (publishing, source sets)
resources/                  # Default FreeMarker templates and knit.properties
testdata/                   # 80+ integration test fixtures (.in.md + expected output)
```

## Core Concepts

### Directives

Knit scans markdown for HTML comment directives:

```markdown
<!--- DIRECTIVE [params] -->
```

Key directives:

| Directive | Effect |
|-----------|--------|
| `KNIT filename.kt` | Emit accumulated code to a file |
| `INCLUDE` | Accumulate hidden boilerplate (imports, helpers) |
| `PREFIX` / `SUFFIX` | Code inserted before/after the main body |
| `CLEAR` | Discard accumulated code without emitting |
| `TEST` / `TEST_NAME` | Generate a JUnit test case from the preceding example |
| `MODULE` | Set module for API link resolution |
| `INDEX` | Insert resolved API reference links |
| `TOC` / `END` | Delimit a region to rewrite as a table of contents |
| `TOC_REF` | Pull TOC from another file |

In `.kt`/`.kts` files, directives appear after `//`, `/*`, or `/**` comment markers.

### File Pattern Auto-Numbering

When `knit.pattern=example-[a-zA-Z0-9-]+-##\.kt` (default), `##` is replaced with a zero-padded counter (e.g., `example-basic-01.kt`, `example-basic-02.kt`). References in markdown like `example-basic-##.kt` auto-increment.

### Property Inheritance

`knit.properties` files are found by walking from each document's directory toward the root. Child properties override parent ones. Key properties:

```properties
knit.dir=src/test/kotlin/example/       # Where to emit .kt files
knit.package=com.example                # Package prefix for generated files
test.dir=src/test/kotlin/example/test/  # Where to emit test classes
test.package=com.example.test
knit.pattern=example-[a-zA-Z0-9-]+-##\.kt
site.root=https://kotlin.github.io/...  # Base URL for API doc links
module.docs=build/dokkaHtml             # Dokka output location
```

### Code Assembly Order

For each emitted example file:

```
PREFIX lines
+  INCLUDE lines  (hidden boilerplate)
+  FreeMarker header (from knit.code.include template)
+  Collected code blocks (in document order)
+  SUFFIX lines
```

### Test Generation

When `TEST` directives are present, knit renders a FreeMarker template (`knit.test.template`) into a JUnit test class. Generated tests call `captureOutput()` from `kotlinx-knit-test` and compare against expected output lines extracted from the markdown. Verification modes: `verifyOutputLines` (exact) or `verifyOutputLinesStart` (prefix match).

### API Link Resolution

1. `pathsaver` Dokka plugin runs during `knitPrepare` and writes `paths-index.json`.
2. During `knit`, `KnitApiParser` loads this index.
3. `[SymbolName]` references in markdown are resolved to full URLs and appended as link definitions.

## Building and Testing

```bash
./gradlew build          # Compile, test, knitCheck
./gradlew knit           # Regenerate all example/test files
./gradlew knitCheck      # Verify files match (no writes)
./gradlew test           # Run integration tests only
```

Integration tests in `test/` copy fixtures from `testdata/` to a temp directory, run the knit processor, and diff output against expected files. To add a test case, add a `.in.md` + `.properties` pair in `testdata/` and a corresponding expected-output directory.

## Key Invariants to Preserve

- **Generated files carry a header comment** ("Do not edit") rendered from `resources/knit.code.include`.
- **knitCheck must pass on CI.** After editing any `.md` file that contains knit directives, run `./gradlew knit` to regenerate before committing.
- **No mutable global state.** `KnitContext` holds per-run state; `KnitProps` is a thread-safe immutable chain.
- **Line separators are preserved.** `KnitUtil` detects existing LF vs CRLF and respects it on write.
- **Warnings-as-errors** is enabled for the Kotlin compiler (`allWarningsAsErrors = true` in `build.gradle.kts`).

## Gradle Plugin Extension

```kotlin
knit {
    rootDir = project.rootDir          // root for file scanning
    siteRoot = "https://..."           // overrides site.root property
    moduleRoots = listOf(".")
    moduleDocs = "build/dokkaHtml"
    dokkaMultiModuleRoot = "build/dokka/htmlMultiModule"
    files = fileTree(rootDir) { ... }  // custom file set
}
```

Tasks registered: `knit`, `knitCheck`, `knitPrepare` (pre-hook for Dokka).

## Modules Published to Maven Central

| Artifact | Purpose |
|----------|---------|
| `org.jetbrains.kotlinx:kotlinx-knit` | The Gradle plugin itself |
| `org.jetbrains.kotlinx:kotlinx-knit-test` | Test helpers for generated test classes |

Current version is in `gradle.properties`.

## Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use four spaces for indentation
- Document public APIs with KDoc comments
- **NEVER** suppress compiler warnings without a documented reason — `allWarningsAsErrors = true` is enforced

## Development Workflow

- **Run `./gradlew build` before submitting PRs** — this compiles, runs tests, and runs `knitCheck`
- After editing any `.md` with knit directives, run `./gradlew knit` to regenerate files before committing
- Add a `testdata/` fixture for any new directive or processing behavior
- Keep PRs focused; prefer separate PRs for unrelated changes

### Commit Guidelines

Use conventional commit prefixes:
- `feat:` — new directive, task, or plugin capability
- `fix:` — bug fix
- `docs:` — README or KDoc changes
- `test:` — test-only changes
- `refactor:` / `chore:` — internal cleanup