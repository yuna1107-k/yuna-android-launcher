#!/usr/bin/env bash
# Downloads the AOSP sources Launcher3 needs to build with Gradle into ../external.
# The tag matches the Launcher3 snapshot (25Q1 / BP1A.250305.020).
set -euo pipefail

TAG="${AOSP_TAG:-android-15.0.0_r20}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/external"
GS="https://android.googlesource.com/platform"

fetch_dir() { # repo, path-in-repo, dest
  mkdir -p "$3"
  curl -sfL "$GS/$1/+archive/refs/tags/$TAG/$2.tar.gz" | tar xz -C "$3"
}
fetch_file() { # repo, path-in-repo, dest-file
  mkdir -p "$(dirname "$3")"
  curl -sfL "$GS/$1/+/refs/tags/$TAG/$2?format=TEXT" | base64 -d > "$3"
}

rm -rf "$OUT"
mkdir -p "$OUT/systemui"
echo "Fetching frameworks/libs/systemui ($TAG)"
curl -sfL "$GS/frameworks/libs/systemui/+archive/refs/tags/$TAG.tar.gz" | tar xz -C "$OUT/systemui"

echo "Fetching SystemUI plugin_core"
fetch_dir frameworks/base packages/SystemUI/plugin_core "$OUT/plugin_core"

echo "Fetching aconfig declarations"
A="$OUT/aconfig"
fetch_file frameworks/base core/java/android/os/flags.aconfig "$A/android_os.aconfig"
fetch_file frameworks/base core/java/android/appwidget/flags.aconfig "$A/android_appwidget.aconfig"
fetch_file frameworks/base libs/WindowManager/Shell/aconfig/multitasking.aconfig "$A/wm_shell.aconfig"
for f in accessibility large_screen_experiences_app_compat lse_desktop_experience \
         responsible_apis wallpaper_manager window_surfaces windowing_frontend windowing_sdk; do
  fetch_file frameworks/base "core/java/android/window/flags/$f.aconfig" "$A/window_$f.aconfig"
done
cp "$OUT"/systemui/aconfig/*.aconfig "$A/"
cp "$ROOT"/aconfig/*.aconfig "$A/"

echo "Done: $OUT"
