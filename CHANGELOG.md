# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.1.5] - 2026-01-13
### Fixed
- **CRITICAL:** Fixed hardcoded "rs" folder in hooks paths - hooks now created in correct project-specific directories (e.g., `src/my_project/hooks/` instead of `src/rs/hooks/`)
- **CRITICAL:** Fixed MySQL scaffolding failures - scaffolding now works correctly with MySQL databases
  - Added catalog parameter to all JDBC metadata queries for MySQL compatibility
  - Fixed empty field vectors issue in generated EDN files
  - Added fallback logic for case-sensitive table names
- **CRITICAL:** Fixed cross-database scaffolding - MySQL now only scaffolds tables from your specific database, not from system databases or other databases on the same server
- Enhanced system table filtering for MySQL (mysql_*, sys_*, information_schema, performance_schema)
### Changed
- Updated `engine-scaffold.clj`: Added `get-catalog-from-connection()` and `normalize-table-name()` helper functions
- Updated `dev.clj`: Added dynamic namespace detection for hooks directory monitoring
- Updated `models-routes.clj`: Added dynamic namespace detection for routes file paths
- Updated all hook template files to use `{{sanitized}}` instead of hardcoded "rs"
- Updated all entity template files to use `{{sanitized}}` instead of hardcoded "rs"
### Compatibility
- Fully backward compatible with SQLite and PostgreSQL
- MySQL projects now work correctly

## [0.1.13] - 2025-08-26
### Fixed
- Fixed malformed template tag `{{name}` in `models-routes.clj` that was preventing project generation.

## [0.1.12] - 2025-08-26
### Changed
- Added error checking to prevent overwriting existing grids, subgrids, dashboards, or reports during generation.

## [0.1.11] - 2025-08-26
### Changed
- Updated `builder.clj` touch utility to trigger wrap-reload after generating grids and related files, improving development workflow.

## [0.1.10] - 2025-08-25
### Fixed
- Fixed error in `layout.clj`.
### Published
- Published new version to Clojars as `org.clojars.hector/lein-template.lst` v0.1.10.
## [Unreleased]

## [0.1.6] - 2025-08-17
### Published
- Published Leiningen template to Clojars as `org.clojars.hector/lein-template.lst` (users run `lein new org.clojars.hector/lst <name>`)
- Disable signing for CI deploys to Clojars (`:sign-releases false`)
## [0.1.5] - 2025-08-17
### Added
- GitHub Actions workflow to publish to Clojars on tag or manual dispatch
- Move template coordinates to verified group `org.clojars.hector/lein-template.lst` and bump version to 0.1.5
## [0.1.4] - 2025-08-17
### Fixed
- Template `project.clj` now uses dotted namespaces (no stray spaces), allowing generated apps to parse and run tests
- VS Code workspace settings to avoid format-on-save corruption of template files
## [0.1.1] - 2019-11-08
### Changed
- Documentation on how to make the widgets.
### Removed
- `make-widget-sync` - we're all async, all the time.
### Fixed
- Fixed widget maker to keep working when daylight savings switches over.
## 0.1.0 - 2019-11-08
### Added
- Files from the new template.
[Unreleased]: https://github.com/hectorqlucero/lst/compare/v0.1.6...HEAD
[0.1.6]: https://github.com/hectorqlucero/lst/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/hectorqlucero/lst/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/hectorqlucero/lst/compare/v0.1.3...v0.1.4
[0.1.1]: https://github.com/your-name/ls/compare/0.1.0...0.1.1
