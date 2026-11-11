#!/bin/bash
# Installs the build tools on Ubuntu 22.04 / 24.04. Run once.
set -euo pipefail

sudo apt-get update
sudo apt-get install -y bc bison build-essential ccache curl flex g++-multilib \
    gcc-multilib git git-lfs gnupg gperf imagemagick lib32readline-dev lib32z1-dev \
    libdw-dev libelf-dev liblz4-tool libsdl1.2-dev libssl-dev libxml2 libxml2-utils \
    lz4 lzop pngcrush protobuf-compiler python3 python-is-python3 rsync schedtool \
    squashfs-tools xsltproc zip zlib1g-dev openjdk-17-jdk android-sdk-platform-tools

mkdir -p "$HOME/bin"
curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o "$HOME/bin/repo"
chmod a+x "$HOME/bin/repo"

echo
echo "Done. Make sure \$HOME/bin is in your PATH, then set your git identity:"
echo '  git config --global user.name  "Your Name"'
echo '  git config --global user.email "you@example.com"'
