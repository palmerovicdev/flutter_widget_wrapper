# Changelog

All notable changes to Flutter Widget Wrapper are documented in this file.
This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Surround With (`Ctrl+Alt+T` / `⌥⌘T`) for Flutter widgets: select a widget, or several
  siblings of a Row/Column/Flex `children:` list, and pick a wrapper.
- Wrapping several selected siblings works with any wrapper whose `${widget}` sits in a
  list (built-in `Stack` or custom ones like `Wrap(children: [${widget}])`); with more
  than one, a searchable chooser opens.
- Optional single "Wrap with…" entry (Settings) that opens a searchable popup instead of
  one `Alt+Enter` entry per wrapper.
- Optional "only on the constructor name" mode (Settings), so wrappers are not offered
  while the caret is inside a widget's arguments.
- Wrapper entries show an icon, and parent-specific wrappers (Flexible under a Row,
  Positioned under a Stack) are listed first.
- The wrapper editor uses a Dart editor with syntax highlighting, shows a live preview
  while typing, and offers existing categories in an editable combo box.
- Settings: speed search in the wrapper tree, double-click to edit, and Dart highlighting
  in the template and preview panes.

- `Alt+Enter` preview for every wrapper. Wrappers with tab-stops (AnimatedSize,
  GestureDetector, InkWell, Align, Positioned, Opacity) had no preview because they
  run as live templates; the preview now shows them with their default values.
- "Create wrapper from …" shows the template it will save in the preview pane, and
  confirms with a notification (with an "Open settings" action) instead of a modal dialog.
- Wrapper warnings are shown: as a hint after wrapping (e.g. InkWell's "needs a
  Material ancestor") and in the Settings detail panel.
- Built-in wrappers can be customized from Settings: editing a built-in saves a custom
  wrapper with the same name that overrides it; deleting the override restores it.
- Import reports how many wrappers were added, replaced, kept and skipped as invalid,
  and asks before replacing custom wrappers with the same name.

### Fixed

- Wrapping a widget with a wrapper that contains a closure (GestureDetector, InkWell,
  or a custom one) inside a `const` expression no longer produces "Invalid constant
  value": the enclosing `const` keywords are removed.
- Intentions of renamed or deleted custom wrappers are unregistered instead of lingering
  for the rest of the session.

### Changed

- Faster `Alt+Enter`: the merged wrapper list is cached per settings state, and the
  widget under the caret is detected once per caret position instead of once per wrapper.
- Intention names, dialog titles and import/export messages moved to the message bundle.

## [1.2.4] - 2026-09-25

### Changed

- Minimum supported IDE raised to IntelliJ IDEA 2026.1.5 (`since-build 261.27258`).
  Older 2026.1.x builds are no longer supported.
- Built against IntelliJ IDEA 2026.1.5 and Dart plugin 509.0.0 (compatible with
  2025.3+ with no upper bound), the Dart release the Marketplace resolves for
  IntelliJ IDEA 2026.2 and 2026.3. The 1.2.3 verification failed because Dart could
  not be resolved for those builds, which made every `com.jetbrains.lang.dart` PSI
  class show up as missing.

## [1.2.3] - 2026-08-14

### Added

- Context-help control next to the Template field in Settings and in the
  create/edit wrapper dialog, explaining `${widget}`, tab-stops, `${end}`, and
  parent rules.

### Fixed

- Custom wrappers no longer disappear from the `Alt+Enter` menu. Startup
  registration read the settings service with `getServiceIfCreated`, which
  returns `null` until something else touches it, so only built-in wrappers were
  registered until the user re-saved the settings. Registration now instantiates
  the service, and it is idempotent and self-healing: the already-registered set
  is derived from `IntentionManager` itself, so a startup run that is cancelled
  or fails midway is repaired on the next sync instead of leaving the menu empty
  for the whole session.

### Changed

- The template syntax help is now a "Syntax help" link that opens a scrollable
  popup instead of a hover tooltip. The reference is taller than a tooltip can
  be, so the tooltip opened under the mouse pointer and flickered, making the
  text unreadable.
- Explicit compatibility range: `since-build 261` with no upper bound, so the
  plugin installs on IntelliJ IDEA 2026.2 and later.
- Replaced the `CheckboxTree(renderer, root)` constructor deprecated in 2026.2
  with the variant that takes an explicit `CheckPolicy`.

- README documents template syntax, tab-stops, parent rules, and how the
  detect → analyze → match → apply pipeline works; settings screenshot updated
  to `assets/settings2.png`.

## [1.2.2] - 2026-07-18

### Added

- Live-template tab-stops in the "Wrap with…" actions: after wrapping, the caret jumps to
  the first editable value and Tab cycles between them, finishing at an optional final
  position. Templates now understand `${name:default}` tab-stops and `${end}` alongside the
  existing `${widget}` placeholder. Built-in wrappers `Opacity`, `Align`, `Positioned`,
  `AnimatedSize`, `GestureDetector` and `InkWell` jump straight to their editable fields.
  Wrappers whose templates contain no markers behave exactly as before.
- Built-in `Positioned` wrapper, offered only for direct children of `Stack`.

## [1.2.1] - 2026-07-18

### Breaking

- The Dart plugin is now a hard dependency. The plugin no longer loads in IDEs without
  Dart; the previous optional config-file hook has been removed.

### Changed

- Widget detection uses the Dart plugin PSI (`DartCallExpression` / related nodes) for
  `.dart` files. The text scanner is kept only for unit tests and non-Dart PSI files;
  it is no longer used as a silent recovery path when PSI returns no hit.

### Fixed

- Named/static calls such as `Theme.of(...)` and `List.generate(...)` are no longer
  treated as wrappable widgets.
- Unchecking a custom wrapper and then editing it no longer re-enables it on Apply.
- Multi-widget selection (text path) requires a `children:` list and uses the same
  widget-name heuristics as single-widget detection.

## [1.2.0] - 2026-07-18

### Added

- Wrap multiple widgets in a `Stack`: select two or more sibling widgets inside a
  `Row`, `Column`, or `Flex` and use `Alt+Enter` → "Wrap N widgets with Stack" to
  wrap them all at once.

### Changed

- Redesigned the settings screen into a master–detail layout: a category tree on the
  left enables or disables built-in and custom wrappers with checkboxes, while the
  right side shows the selected wrapper's template and a live preview. Add, edit,
  duplicate, import, and export are available from the toolbar.

### Fixed

- Widget detection now correctly handles raw strings (`r'...'`), triple-quoted strings,
  and `${widget}`-style interpolation, so a string earlier in the file no longer
  desyncs the detection of widgets below it.
- Non-widget constructors such as `Duration`, `Color`, and `TextStyle` are no longer
  offered as wrappable widgets in the `Alt+Enter` menu.

### Removed

- The built-in `Expanded` wrapper (it was frequently suggested incorrectly; use
  `Flexible` or a custom wrapper when needed).

## [1.1.1] - 2026-07-01

### Fixed

- Dynamic wrapper intentions now use distinct implementation identities, preventing
  IntelliJ Platform from rejecting multiple registered wrappers.
- Updated Kotlin string templates for compatibility with the current compiler.
- Settings controls now use IDE-scaled insets for consistent UI rendering.

## [1.1.0] - 2026-07-01

### Added

- Settings page for enabling and disabling built-in wrappers.
- Custom wrapper editor with form-based creation, JSON editing, validation,
  preview, import, and export.
- Intention for turning an existing Flutter widget into a reusable wrapper.
- Context rules, descriptions, categories, and warnings for wrapper definitions.

### Changed

- Wrapper registration now updates when configuration changes.
- Settings layout and wrapper management workflow were improved.

## [1.0.0] - 2026-07-01

### Added

- Initial Flutter widget detection and `Alt+Enter` integration.
- Built-in wrappers for animation, interaction, layout, scrolling, and visual
  use cases.
- Context-aware availability for `Expanded` and `Flexible`.
- Automatic indentation and code reformatting after wrapping.
