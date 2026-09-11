# Contributing to J XA Tester

Thanks for your interest in contributing.

## Ground rules

- Be respectful and collaborative.
- Follow the [Code of Conduct](./CODE_OF_CONDUCT.md).
- Keep changes focused, small, and test-backed.

## Development setup

1. Fork and clone the repository.
2. Ensure Java 17+ and Maven are installed.
3. From the repository root, run:

```bash
mvn clean verify
```

This project is a multi-module Maven reactor. Running from the root validates all modules.

## Making changes

- Create a branch from the default branch.
- Prefer clear, isolated commits.
- Add or update tests for behavioral changes.
- Update documentation when behavior or usage changes.

## Style and quality expectations

- Keep APIs and module boundaries consistent with existing code.
- Avoid unrelated refactors in the same pull request.
- Do not introduce secrets, credentials, or sensitive data.

## Pull request process

1. Ensure `mvn clean verify` passes locally.
2. Open a pull request with:
   - a clear summary,
   - rationale for the change,
   - testing notes.
3. Link related issues when applicable.
4. Address review feedback promptly.

## CI and approvals

Pull request workflows may require environment approval before jobs start. If your checks are waiting, a maintainer may need to approve the workflow run.

## Reporting bugs and requesting features

- Use GitHub Issues for bug reports and feature requests.
- Include reproduction steps, expected behavior, and actual behavior for bugs.

Thanks again for helping improve J XA Tester.
