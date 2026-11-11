#!/bin/bash
# Builds the ROM. The first build takes several hours.
set -euo pipefail
source "$(dirname "$0")/common.sh"

"$(dirname "$0")/3-apply-changes.sh"

cd "$ROM_DIR"
export USE_CCACHE=1 CCACHE_EXEC=/usr/bin/ccache
ccache -M 50G >/dev/null

set +u
source build/envsetup.sh
brunch "$DEVICE"

echo
echo "Finished. Files to flash are in:"
ls -1 "$ROM_DIR/out/target/product/$DEVICE"/lineage-*.zip "$ROM_DIR/out/target/product/$DEVICE"/recovery.img 2>/dev/null || true
