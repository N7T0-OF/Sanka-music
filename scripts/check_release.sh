#!/usr/bin/env bash
# Check the GitHub Actions release and GitHub Release for a tag.
# Prerequisites: gh CLI authenticated (gh auth login), jq.
# Usage: ./scripts/check_release.sh [TAG] [--watch] [--interval SECONDS] [--timeout SECONDS]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

TAG="v1.1.6"
WATCH=false
INTERVAL=15
TIMEOUT=900
WORKFLOW="release.yml"

usage() {
  sed -n '2,5p' "${BASH_SOURCE[0]}"
  cat <<'HELP'

Options:
  --watch                 Poll until the workflow finishes or the release exists
  --interval SECONDS      Delay between checks (default: 15)
  --timeout SECONDS       Maximum watch time (default: 900)
  -h, --help              Show this help
HELP
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --watch)
      WATCH=true
      shift
      ;;
    --interval)
      [[ $# -ge 2 ]] || { echo "Missing value for --interval" >&2; exit 2; }
      INTERVAL="$2"
      shift 2
      ;;
    --timeout)
      [[ $# -ge 2 ]] || { echo "Missing value for --timeout" >&2; exit 2; }
      TIMEOUT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    v[0-9]*|[0-9]*)
      [[ "$1" == v* ]] && TAG="$1" || TAG="v$1"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] || {
  echo "Invalid tag: $TAG (expected vX.Y.Z)" >&2
  exit 2
}
[[ "$INTERVAL" =~ ^[1-9][0-9]*$ ]] || { echo "Interval must be a positive integer." >&2; exit 2; }
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo "Timeout must be a positive integer." >&2; exit 2; }

command -v gh >/dev/null 2>&1 || {
  echo "gh CLI is required. Install it from https://cli.github.com/" >&2
  exit 1
}
command -v jq >/dev/null 2>&1 || {
  echo "jq is required. Install it with your system package manager." >&2
  exit 1
}
gh auth status >/dev/null 2>&1 || {
  echo "GitHub CLI is not authenticated. Run: gh auth login" >&2
  exit 1
}

REPO="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
TAG_REF="$(gh api "repos/$REPO/git/ref/tags/$TAG" 2>/dev/null || true)"
TAG_SHA="$(jq -r '.object.sha // empty' <<<"$TAG_REF")"
TAG_TYPE="$(jq -r '.object.type // empty' <<<"$TAG_REF")"
if [[ "$TAG_TYPE" == "tag" && -n "$TAG_SHA" ]]; then
  TAG_SHA="$(gh api "repos/$REPO/git/tags/$TAG_SHA" --jq '.object.sha' 2>/dev/null || true)"
fi

if [[ -z "$TAG_SHA" ]]; then
  echo "Tag $TAG was not found in $REPO."
  exit 1
fi

echo "Repository: $REPO"
echo "Tag:        $TAG ($TAG_SHA)"

print_release() {
  local release_json
  release_json="$(gh release view "$TAG" --json isDraft,isPrerelease,publishedAt,url,assets 2>/dev/null || true)"
  if [[ -z "$release_json" ]]; then
    return 1
  fi

  echo "Release:    $(jq -r '.url')"
  echo "Published:  $(jq -r '.publishedAt // "not yet"')"
  echo "Draft:      $(jq -r '.isDraft')"
  echo "Prerelease: $(jq -r '.isPrerelease')"
  echo "Assets:"
  jq -r '.assets[] | "  - \(.name) (\(.size) bytes)"' <<<"$release_json"
  return 0
}

print_run() {
  local run_json="$1"
  local status conclusion url created_at
  status="$(jq -r '.status // "unknown"' <<<"$run_json")"
  conclusion="$(jq -r '.conclusion // "pending"' <<<"$run_json")"
  url="$(jq -r '.url // ""' <<<"$run_json")"
  created_at="$(jq -r '.createdAt // "unknown"' <<<"$run_json")"
  echo "Workflow:   $status / $conclusion"
  echo "Started:    $created_at"
  echo "Run URL:    ${url:-unavailable}"
}

find_run() {
  gh run list \
    --workflow "$WORKFLOW" \
    --limit 20 \
    --json databaseId,status,conclusion,headSha,event,url,createdAt,displayTitle \
    --jq "map(select(.headSha == \"$TAG_SHA\" or (.displayTitle | test(\"$TAG\")))) | .[0]" \
    2>/dev/null || true
}

started_at="$(date +%s)"
while :; do
  run_json="$(find_run)"
  if [[ -n "$run_json" && "$run_json" != "null" ]]; then
    print_run "$run_json"
    run_status="$(jq -r '.status' <<<"$run_json")"
    run_conclusion="$(jq -r '.conclusion // ""' <<<"$run_json")"

    if [[ "$run_status" == "completed" ]]; then
      if [[ "$run_conclusion" != "success" ]]; then
        echo "Release workflow failed: $run_conclusion" >&2
        exit 1
      fi
      if print_release; then
        echo "Release $TAG is published successfully."
        exit 0
      fi
      echo "Workflow succeeded, but the GitHub release is not visible yet."
      [[ "$WATCH" == true ]] || exit 1
    fi
  else
    echo "Workflow:   no matching run found yet"
    if [[ "$WATCH" == false ]]; then
      echo "Run the 'Build and Release' workflow manually with tag $TAG."
      exit 1
    fi
  fi

  [[ "$WATCH" == true ]] || {
    if print_release; then
      echo "Release $TAG is published successfully."
      exit 0
    fi
    echo "Release $TAG is not published yet."
    exit 1
  }

  elapsed=$(( $(date +%s) - started_at ))
  if (( elapsed >= TIMEOUT )); then
    echo "Timed out after ${TIMEOUT}s while waiting for release $TAG." >&2
    exit 1
  fi
  echo "Checking again in ${INTERVAL}s..."
  sleep "$INTERVAL"
done
