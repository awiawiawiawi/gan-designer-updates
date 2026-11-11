#!/bin/bash
# Copies our changes from this repository into the LineageOS source tree.
# Safe to run again after editing the allowlist or the code.
set -euo pipefail
source "$(dirname "$0")/common.sh"

# vendor/extra: product settings and the install allowlist.
mkdir -p "$ROM_DIR/vendor/extra"
rsync -a --delete "$REPO_ROOT/rom/vendor_extra/" "$ROM_DIR/vendor/extra/"

# frameworks/base: new source files, then the patch to existing files.
cd "$ROM_DIR/frameworks/base"
git checkout -q -- services/core/java/com/android/server/pm/InstallPackageHelper.java
cp -r "$REPO_ROOT/rom/frameworks_base/." .
git apply --verbose "$REPO_ROOT"/rom/patches/frameworks_base-*.patch

echo "Changes applied to $ROM_DIR"
