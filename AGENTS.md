# Repository Guidelines

## Project Structure & Module Organization

This repository is currently a clean scaffold with no source tree committed yet. Keep future code organized by responsibility and avoid implementation files at the root. Recommended layout:

- `src/` for application or library source code.
- `tests/` for unit, integration, and regression tests.
- `assets/` for static files such as images, fixtures, or sample data.
- `docs/` for design notes, API references, and contributor documentation.
- `scripts/` for repeatable local automation.

Keep configuration files at the root only when expected by the toolchain, such as `package.json`, `pyproject.toml`, `Cargo.toml`, or `Makefile`.

## Build, Test, and Development Commands

No build system is committed yet. When adding one, document canonical commands here and keep them stable. Prefer a small command surface:

- `make setup` to install or verify local dependencies.
- `make test` to run the full test suite.
- `make lint` to run formatting and static checks.
- `make dev` to start the local development server or watcher.

If the project uses a language-specific tool directly, include examples such as `npm test`, `pytest`, `cargo test`, or `go test ./...`.

## Coding Style & Naming Conventions

Follow the formatter and linter native to the chosen stack. Commit formatter configuration with the first source files so contributors get consistent output. Use descriptive names, keep modules focused, and prefer language conventions: `snake_case` for Python files, `kebab-case` for frontend route or asset files, and `PascalCase` for React components or exported types where applicable.

## Testing Guidelines

Add tests with the feature or fix they cover. Place tests under `tests/` or beside source files only if the chosen framework convention favors colocated tests. Use clear names that describe behavior, for example `test_search_returns_ranked_results` or `SearchBox.submits-query.test.tsx`. Include regression tests for bug fixes.

## Commit & Pull Request Guidelines

This repository has no commit history yet, so use concise imperative commit messages, such as `Add search indexing module` or `Fix empty query handling`. Keep commits focused.

Pull requests should include a short summary, validation steps, linked issues when relevant, and screenshots or recordings for UI changes. Note follow-up work explicitly so reviewers can separate intentional scope from omissions.

## Security & Configuration Tips

Do not commit secrets, local credentials, generated private keys, or machine-specific configuration. Provide example environment files such as `.env.example` when configuration is required, and document required variables in `docs/` or the project README.
