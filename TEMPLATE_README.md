# LST/WebGen Leiningen Template

This is a Leiningen template for creating new LST/WebGen parameter-driven web applications.

## Installation

The template is published to Clojars at `org.clojars.hector/webgen`.

### Usage

Create a new project directly from Clojars:
```bash
lein new org.clojars.hector/webgen myapp
cd myapp
```

### Local Development (for template maintainers)
```bash
cd /path/to/webgen
lein install  # Install locally for testing
lein new org.clojars.hector/webgen test-project  # Test local version
```

Follow the instructions displayed after project creation.

## Template Structure

The template includes:
- Complete source code (`src/`, `dev/`, `test/`)
- Entity configurations and hooks
- Database migrations for multiple databases (MySQL, PostgreSQL, SQLite)
- Comprehensive documentation
- Example entities: users, contactos, cars, siblings
- Auto-reload development environment

## Publishing to Clojars

1. Ensure you're logged into Clojars:
   ```bash
   lein deploy clojars
   ```

2. Update version in `project.clj` before each release

3. Users can then install with:
   ```bash
   lein new org.clojars.hector/webgen myproject
   ```

## Template Features

- JAR-compatible resource copying (works from installed JAR)
- Mustache template rendering for project.clj and README.md
- Copies all source files, migrations, entities, and documentation
- Creates proper .gitignore for config files
- Includes config.clj.example for easy setup

## Technical Notes

### Directory Structure

Following Leiningen template conventions:
- `src/leiningen/new/webgen.clj` - Template generator code
- `resources/leiningen/new/webgen/` - Template resource files

### JAR Resource Copying

The template uses a sophisticated mechanism to copy resources that works both:
- During development (filesystem resources)
- After installation (JAR resources)

The `get-resource-paths` function introspects JAR files or filesystem directories
to enumerate all resources, then `copy-resource` streams them to the destination.

### Files Handled Specially

- `project.clj` - Rendered with mustache, substitutes {{name}}
- `README.md` - Rendered with mustache, substitutes {{name}}
- `.gitignore` - Generated with content
- All other files - Copied as-is

## Version History

- 0.1.0 - Initial release with JAR support
