---
description: Create a new feature branch, commit changes, and prepare for PR
---

# New Fix Workflow

This workflow is used when implementing fixes or features in an existing repository (not a new repo).

## Steps

1. **Create a new feature branch**
   ```bash
   git checkout -b fix/your-fix-name
   ```
   OR for features:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Stage and commit your changes**
   ```bash
   git add .
   git commit -m "feat/fix: description of your changes"
   ```
   
   Follow conventional commit format:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `refactor:` for code refactoring
   - `docs:` for documentation changes
   - `test:` for test changes
   - `chore:` for maintenance tasks

3. **Push the branch to remote**
   ```bash
   git push -u origin fix/your-fix-name
   ```

4. **Create a Pull Request**
   - Go to GitHub/GitLab and create a PR from your branch to main
   - Include a clear description of changes
   - Reference any related issues

## Important Notes

- **NEVER commit directly to main branch**
- Always work on a feature/fix branch
- Ensure tests pass before committing
- Follow the project's commit message convention
- Request code review before merging
