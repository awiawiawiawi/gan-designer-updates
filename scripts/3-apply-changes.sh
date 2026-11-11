#!/bin/bash
# Copies our changes from this repository into the LineageOS source tree.
# Safe to run again after editing the allowlist or the code.
set -euo pipefail
source "$(dirname "$0")/common.sh"

if [ ! -f "$REPO_ROOT/rom/vendor_extra/gan/device_config.json" ]; then
    echo "WARNING: rom/vendor_extra/gan/device_config.json is missing."
    echo "         The ROM will be built WITHOUT remote management."
    echo "         Run 'server/gan_server.py init' on the server and copy the file it creates."
fi

# vendor/extra: product settings, the install allowlist and the management agent.
mkdir -p "$ROM_DIR/vendor/extra"
rsync -a --delete "$REPO_ROOT/rom/vendor_extra/" "$ROM_DIR/vendor/extra/"

# frameworks/base: new source files, then the patch to existing files.
cd "$ROM_DIR/frameworks/base"
git checkout -q -- services/core/java/com/android/server/pm/InstallPackageHelper.java
cp -r "$REPO_ROOT/rom/frameworks_base/." .
git apply --verbose "$REPO_ROOT"/rom/patches/frameworks_base-*.patch

echo "Changes applied to $ROM_DIR"
