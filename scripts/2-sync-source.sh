#!/bin/bash
# Downloads the LineageOS source and the A21s device code (about 150 GB).
set -euo pipefail
source "$(dirname "$0")/common.sh"

mkdir -p "$ROM_DIR/.repo/local_manifests"
cd "$ROM_DIR"

repo init -u https://github.com/LineageOS/android.git -b "$BRANCH" --git-lfs --no-clone-bundle

# Device tree, kernel and Samsung's closed hardware files.
cat > .repo/local_manifests/gan.xml <<XML
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <remote name="muppets" fetch="https://github.com/TheMuppets" />
  <project name="LineageOS/android_device_samsung_a21s" path="device/samsung/a21s" remote="github" />
  <project name="proprietary_vendor_samsung_a21s" path="vendor/samsung/a21s" remote="muppets" revision="$BRANCH" />
  <project name="proprietary_vendor_samsung_a21s-common" path="vendor/samsung/a21s-common" remote="muppets" revision="$BRANCH" />
</manifest>
XML

repo sync -c -j"$(nproc)" --force-sync --no-tags

# Fetches the remaining device repositories (kernel, hardware code).
set +u
source build/envsetup.sh
breakfast "$DEVICE"
