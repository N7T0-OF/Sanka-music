#!/bin/bash
# Release workflow: auto-version from git tag → build → sign → upload to GitHub
# Prerequisites:
#   - gh CLI authenticated (gh auth login)
#   - KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars set
#   - ANDROID_HOME set
# Usage: ./scripts/release.sh [--draft] [--prerelease]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

DRAFT="--draft"
PRERELEASE=""
RELEASE_NOTES=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --draft) DRAFT="--draft" ;;
    --prerelease) PRERELEASE="--prerelease" ;;
    --notes) RELEASE_NOTES="$2"; shift ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
  shift
done

# ── 1. Check prerequisites ──
command -v gh >/dev/null 2>&1 || { echo "gh CLI required. Install: https://cli.github.com/"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh not logged in. Run: gh auth login"; exit 1; }

: "${KEYSTORE_PASSWORD:?KEYSTORE_PASSWORD not set}"
: "${KEY_ALIAS:?KEY_ALIAS not set}"
: "${KEY_PASSWORD:?KEY_PASSWORD not set}"
: "${ANDROID_HOME:?ANDROID_HOME not set}"

# ── 2. Auto-bump version from git tag ──
echo "=== Step 1: Auto-version from git tag ==="
./gradlew autoVersion --no-configuration-cache

# Read back the bumped version for the release name
VERSION_NAME=$(grep 'version-name\s*=' gradle/libs.versions.toml | head -1 | sed 's/.*"\(.*\)"/\1/')

if git rev-parse "v$VERSION_NAME" >/dev/null 2>&1; then
  echo "Tag v$VERSION_NAME already exists. Release would be a duplicate."
  echo "Create a new git tag first: git tag vX.Y.Z && git push --tags"
  exit 1
fi

# ── 3. Build & sign ──
echo "=== Step 2: Build & sign APK ==="
./build_and_sign_apk.sh

# ── 4. Find signed APKs ──
APK_DIR="./androidApp/build/outputs/apk/release"
SIGNED_APKS=()
while IFS= read -r -d '' f; do
  [[ "$f" != *unsigned* && "$f" != *aligned* && "$f" != *.idsig ]] && SIGNED_APKS+=("$f")
done < <(find "$APK_DIR" -name "*.apk" -print0)

if [ ${#SIGNED_APKS[@]} -eq 0 ]; then
  echo "No signed APK found in $APK_DIR"
  exit 1
fi
echo "Signed APKs: ${SIGNED_APKS[*]}"

# ── 5. Create git tag ──
echo "=== Step 3: Create git tag v$VERSION_NAME ==="
git tag "v$VERSION_NAME"
git push origin "v$VERSION_NAME"

# ── 6. GitHub release ──
echo "=== Step 4: Create GitHub release ==="
RELEASE_ARGS=(
  gh release create "v$VERSION_NAME"
  --title "LiquidMusic $VERSION_NAME"
  --generate-notes
  $DRAFT
  $PRERELEASE
)

for apk in "${SIGNED_APKS[@]}"; do
  RELEASE_ARGS+=("$apk")
done

# shellcheck disable=SC2068
${RELEASE_ARGS[@]}

echo ""
echo "=== Done ==="
echo "Release v$VERSION_NAME created on GitHub"
echo "APKs uploaded: ${SIGNED_APKS[*]}"