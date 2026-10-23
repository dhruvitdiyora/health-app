## What and why
<!-- One or two sentences. Link the issue if there is one. -->

## How it was verified
- [ ] `./init.sh` passes (build, unit tests, no network permission)
- [ ] UI changes checked on a device in light **and** dark theme
- [ ] Photo model / preprocessing changes: `BenchActivity` run and compared with PC results
- [ ] Room schema changes ship with a migration

## Hard constraints (CLAUDE.md)
- [ ] No network access added; no analytics or remote services
- [ ] Estimates still shown as a range and never auto-saved
- [ ] No model weights, datasets, absolute paths or personal data committed
