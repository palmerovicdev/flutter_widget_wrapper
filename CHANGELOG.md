# Changelog

All notable changes to Flutter Widget Wrapper are documented in this file.
This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
