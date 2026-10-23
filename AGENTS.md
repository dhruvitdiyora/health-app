# Agent Guide: see CLAUDE.md

The full, authoritative guide for coding agents lives in **[CLAUDE.md](CLAUDE.md)** at the repository root. **Read it before making changes.** It covers the hard constraints (no network, no auto-saved estimates, no test data on the user's phone), the verification gate (`./init.sh`) and links to every topic doc.

This file exists so that agents following the `AGENTS.md` convention find the guide. It is deliberately a real file rather than a symlink, so it works on every checkout. `CLAUDE.md` is the single source of truth; keep guidance there, not here.
