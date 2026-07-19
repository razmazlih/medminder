#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export XDG_CONFIG_HOME="${XDG_CONFIG_HOME:-/tmp/codex-firebase-config}"

echo "Using Firebase project: $(cat .firebaserc | tr -d '\n')"
echo "Building Cloud Functions..."
cd functions
npm run build
cd "$ROOT_DIR"

echo "Deploying Firestore rules + sync functions..."
firebase deploy --only \
  functions:joinDataset,functions:loginWebWithSyncCode,functions:logoutWebSession,functions:revokeDataset,firestore:rules

echo "Done."
