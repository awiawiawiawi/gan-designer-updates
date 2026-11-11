# Shared settings for the build scripts.
DEVICE=a21s
BRANCH=lineage-22.2
ROM_DIR="${ROM_DIR:-$HOME/android/lineage}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="$HOME/bin:$PATH"
