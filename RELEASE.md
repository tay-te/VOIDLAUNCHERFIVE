# VOID Launcher — Release Workflow

## Prerequisites

### GitHub Secrets

Go to **Settings → Secrets and variables → Actions** in the repo and add:

| Secret | Value |
|--------|-------|
| `VITE_SUPABASE_URL` | Your Supabase project URL |
| `VITE_SUPABASE_ANON_KEY` | Your Supabase anon key |

`GITHUB_TOKEN` is provided automatically by GitHub Actions.

### App Icon (Optional)

Place icon files in `public/`:
- `icon.icns` — macOS
- `icon.ico` — Windows

Then uncomment the icon lines in `forge.config.ts` (search for "uncomment when").

## Publishing a Release

### 1. Bump the version

```bash
npm version patch   # 1.0.0 → 1.0.1
npm version minor   # 1.0.0 → 1.1.0
npm version major   # 1.0.0 → 2.0.0
```

This updates `package.json` and creates a git tag (e.g. `v1.0.1`).

### 2. Push to trigger CI

```bash
git push origin main --tags
```

GitHub Actions builds for all platforms:
- macOS ARM (Apple Silicon)
- macOS Intel
- Windows x64

Artifacts are uploaded as a **draft** GitHub Release.

### 3. Publish the release

1. Go to **Releases** in the GitHub repo
2. Find the new draft release
3. Edit the description if needed
4. Click **Publish release**

Users running the app will receive the update automatically within 30 minutes.

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

- The app checks GitHub Releases for new versions on launch and every 30 minutes
- Updates download in the background
- Installed automatically on next app quit
- Users see an in-app notification when an update is available/downloaded

## Code Signing (Future)

Without code signing:
- **macOS**: Users see "unidentified developer" — they must right-click → Open
- **Windows**: SmartScreen may warn on first run

To add signing:
- macOS: Get an Apple Developer ID, add `osxSign` and `osxNotarize` to `packagerConfig` in `forge.config.ts`
- Windows: Get a code signing certificate, configure in `MakerSquirrel` options
