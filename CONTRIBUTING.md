# Contributing

Thanks for taking a look. This is a personal app, so larger changes are best discussed in an issue first.

## Ground rules
Read [CLAUDE.md](CLAUDE.md) — its **Hard Constraints** apply to everyone, not just coding agents. In short: no
network access, estimates are never auto-saved, no test data on real devices, no model weights or personal data in
git, and every schema change ships with a migration.

## Workflow
1. Branch from `main`.
2. Make the change; keep it small and focused.
3. Run `./init.sh` (build + unit tests). For UI changes also check light and dark themes on a device.
4. Commit with Conventional Commits — `git config commit.template .gitmessage` shows the format and scopes.
5. Open a pull request using the template; say what you verified and how.

## Code style
- Kotlin official style; match the surrounding code's naming, comments and idioms.
- Comments explain *why*, not *what*.
- Pure logic goes in `domain/` with unit tests; Android-specific code stays in `data/`, `ml/` and `ui/`.
