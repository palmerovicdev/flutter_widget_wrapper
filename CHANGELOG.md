# Changelog

All notable changes to Flutter Widget Wrapper are documented in this file.
This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
