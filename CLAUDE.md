# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An IntelliJ Platform plugin (Kotlin) that adds context-aware "Wrap with _Widget_"
actions to the `Alt+Enter` intention menu in Flutter/Dart files. It detects the
Flutter widget under the caret, decides which wrappers are valid for that context,
and rewrites the source — preserving indentation and reformatting via the IDE.

Target platform: IntelliJ IDEA 2026.1+ with the Dart plugin (a hard `<depends>Dart</depends>`).

## Commands

Everything goes through the Gradle Wrapper (`./gradlew`):

- `./gradlew check` — compile + run all tests (this is what the "Run Tests" run config uses)
- `./gradlew test` — run unit/integration tests only
- `./gradlew test --tests "com.palmerodev.fww.PsiFlutterWidgetDetectorTest"` — single test class
- `./gradlew test --tests "com.palmerodev.fww.*DetectorTest"` — pattern match
- `./gradlew runIde` — launch a sandbox IDE with the plugin installed (sandbox lives under `.intellijPlatform/sandbox/`)
- `./gradlew buildPlugin` — build distributable ZIP into `build/distributions/`
- `./gradlew verifyPlugin` — run the JetBrains Plugin Verifier

The user typically runs and debugs from IntelliJ IDEA Ultimate using the `.run/`
configurations (Run Tests, Run Plugin, Run Verifications) rather than the CLI.

## Architecture

### Detection → context → matching → templating pipeline

The core flow, invoked from every `WrapWithWidgetIntention.isAvailable`/`invoke`:

1. **`FlutterWidgetDetector.detect(file, offset)`** — the single entry point. Prefers
   the Dart PSI (`PsiFlutterWidgetDetector`) when the file is a real `DartFile`; falls
   back to `TextFlutterWidgetDetector` (a hand-rolled scanner) otherwise, e.g. in unit
   tests that only pass raw text. Returns a `DetectedWidget` (name, `TextRange`, source
   text, parent widget name, ancestor chain).
2. **`FlutterContextAnalyzer.analyze(detected)`** → `FlutterWidgetContext` (adds derived
   flags like `isDirectChildOfFlex`, `isInsideStack`).
3. **`WrapperContextMatcher.matches(wrapper, context)`** — decides visibility using the
   wrapper's `allowedParents` / `disallowedParents` / `requiresDirectParent` rules
   (e.g. `Expanded`/`Flexible` only appear as direct children of `Row`/`Column`/`Flex`;
   `Positioned` only as a direct child of `Stack`).
4. **`WrapperTemplateEngine.apply(wrapper, widgetText, baseIndent)`** — substitutes the
   detected source into the template's `${widget}` placeholder, handling multi-line
   widgets and re-indentation. The document edit then runs `CodeStyleManager.reformatRange`.

**Widget vs. non-widget disambiguation** lives in `WidgetNameHeuristics`: an
upper-camel identifier is treated as a widget unless it's in the `NON_WIDGET_TYPES`
denylist (`Duration`, `Color`, `TextStyle`, `EdgeInsets`, …). This is what keeps
constructor calls like `EdgeInsets.all(8)` out of the wrap menu. Both PSI and text
detectors funnel through it.

### Dynamic intention registration (the non-obvious part)

Wrap intentions are **not** declared in `plugin.xml`. Instead
`WrapIntentionRegistrar` (a `postStartupActivity`) calls
`IntentionManager.getInstance().addAction(...)` at runtime — one
`RegisteredWrapWithWidgetIntention` per wrapper (built-in + valid custom), plus the
singleton `CreateWrapperFromWidgetIntention` and `WrapSelectionWithStackIntention`.

Critical constraint: IntelliJ's `IntentionManagerImpl.checkForDuplicates()` throws and
hides **all** intentions if two registered intentions share an implementation class.
Because every wrapper reuses the same `WrapWithWidgetIntention` class,
`RegisteredWrapWithWidgetIntention` wraps it as an `IntentionActionDelegate` and returns
a **unique** `getImplementationClassName()` per wrapper name. Do not collapse this
indirection. `syncRegistrations()` is idempotent (guarded by `registeredNames`) and is
re-called after the user adds a custom wrapper in settings.

### Wrappers: built-in + custom, merged

- `WidgetWrapper` (in `model/`) is the definition: `name`, `template` (list of lines),
  `category`, `enabled`, parent rules, optional `warning`.
- `BuiltInWrappers.ALL` holds the shipped wrappers (Align, AnimatedSize, Expanded,
  Flexible, GestureDetector, InkWell, Opacity, Positioned, SafeArea, SingleChildScrollView, Stack).
- Custom wrappers are stored as a JSON string in application settings and parsed by
  `WrapperJsonCodec` (Gson). `WrapperValidator` requires a non-blank name, a non-empty
  template, and the literal `${widget}` placeholder.
- `WrapperRepository.merge()` combines built-ins (respecting `disabledBuiltInNames`) with
  custom wrappers; custom entries override a built-in of the same name. `all()` /
  `byName()` are the read API used by intentions.

### Settings persistence

`FlutterWrapperSettings` is an app-level `@Service` / `PersistentStateComponent`
(`FlutterWidgetWrapper.xml`) storing exactly two fields: `customWrappersJson` and
`disabledBuiltInNames`. `WrapperSettingsConfigurable` + `WrapperFormDialog` are the
Swing UI (master–detail category tree + template/preview). Always fetch the service via
`getInstance()` / `getInstanceOrNull()` — the latter returns null when the service isn't
yet created (used during startup registration).

### The two secondary intentions

- **`CreateWrapperFromWidgetIntention`** — turns an existing widget whose child slot is
  `child`/`children`/`sliver`/`slivers` (`WrappableFieldDetector`) into a reusable custom
  wrapper by templating that slot with `${widget}`, then opens `WrapperFormDialog`.
- **`WrapSelectionWithStackIntention`** — when ≥2 sibling widgets are selected inside a
  flex `children:` list (`MultiWidgetSelectionDetector`), replaces the selection with a
  single `Stack` containing them. Detector also prefers PSI with a text-scan fallback.

## Conventions

- **Kotlin multi-dollar string literals** (`$$"...${widget}"`) are used throughout to
  write the literal `${widget}` placeholder without interpolation. This requires the
  Kotlin 2.3.x compiler pinned in `settings.gradle.kts`. Keep using `$$"..."` for
  placeholder-bearing strings; don't switch to escaped `\${...}`.
- **Dart PSI classes** (`DartCallExpression`, `DartNewExpression`, `DartListLiteralExpression`,
  etc.) come from the Marketplace `Dart` plugin dependency declared in `build.gradle.kts`,
  not from a bundled module.
- **User-facing strings** live in `messages/FlutterWidgetWrapperBundle.properties`, accessed
  via `FlutterWidgetWrapperBundle.message(key, ...)`.
- Tests extend `BasePlatformTestCase` (IntelliJ `TestFrameworkType.Platform`) and use
  `myFixture.configureByText("main.dart", ...)`. Note that a bare top-level `Text(...)`
  parses as an incomplete declaration — wrap snippets in a function body
  (`void f() { ... }`) so the Dart parser produces real call expressions.
- **Versioning**: bump `gradle.properties` `version` and add a `CHANGELOG.md` entry
  (Keep a Changelog format) before a release; `README.md` also carries a version badge.
