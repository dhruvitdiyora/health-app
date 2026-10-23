# Security

This is a personal project, but security reports are welcome.

## Reporting
Please report privately — use GitHub's **"Report a vulnerability"** (Security tab → Advisories) on this repository.
Don't open a public issue with exploit details.

Include what you can: a description and impact, steps to reproduce, the app version (Settings → About) and your
Android version.

## Scope worth reporting
- Any way the app could send data off the device (it is meant to have no network access at all).
- Other apps reading this app's data (database, cache, the FileProvider, exported activities).
- Health Connect data being written without the user turning on sharing.
