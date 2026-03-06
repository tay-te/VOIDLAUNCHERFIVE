# VOID Launcher — Release Workflow

## Prerequisites

### GitHub Secrets

Go to **Settings → Secrets and variables → Actions** in the repo and add:

| Secret | Value |
|--------|-------|
| `VITE_SUPABASE_URL` | Your Supabase project URL |
| `VITE_SUPABASE_ANON_KEY` | Your Supabase anon key |

`GITHUB_TOKEN` is provided automatically by GitHub Actions.

### App Icon

Icon files are in `public/`:
- `icon.png` — Source (2000x2000)
- `icon.icns` — macOS
- `icon.ico` — Windows

These are generated from `void.png` and configured in `forge.config.ts`.

## Publishing a Release

### 1. Bump the version

```bash
npm version patch   # 2.0.4 → 2.0.5
npm version minor   # 2.0.4 → 2.1.0
npm version major   # 2.0.4 → 3.0.0
```

This updates `package.json` and creates a git tag (e.g. `v2.0.5`).

### 2. Push to trigger CI

```bash
git push origin main --tags
```

GitHub Actions builds for:
- **macOS** — Universal binary (works on both Apple Silicon and Intel)
- **Windows** — x64

The CI workflow automatically syncs the `package.json` version to match the git tag, so artifacts always upload to the correct release.

Artifacts are uploaded as a **published** GitHub Release (not a draft), which is required for auto-update to work.

### 3. Verify the release

1. Go to **Releases** in the GitHub repo
2. Confirm the new release has `.dmg`, `.zip`, and `.exe` assets attached
3. Edit the description if needed

## Local Build (Manual)

```bash
# Development
npm start

# Package (no installer)
npm run package

# Build installers (DMG + ZIP)
npm run make

# Build and upload to GitHub Releases
npm run publish
```

Artifacts output to `out/make/`.

## How Auto-Update Works

- Uses `electron-updater` with the GitHub provider
- Checks GitHub Releases for new versions on launch and every 30 minutes
- Updates download in the background
- Installed automatically on next app quit
- Users see an in-app notification when an update is available/downloaded
- Releases must be **published** (not draft) for the updater to detect them

## Code Signing (Future)

Without code signing:
- **macOS**: Users see "unidentified developer" — they must right-click → Open
- **Windows**: SmartScreen may warn on first run

To add signing:
- macOS: Get an Apple Developer ID, add `osxSign` and `osxNotarize` to `packagerConfig` in `forge.config.ts`
- Windows: Get a code signing certificate, configure in `MakerSquirrel` options
