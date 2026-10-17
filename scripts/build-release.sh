#!/usr/bin/env bash
# Builds, verifies and fingerprints a signed release APK of AI Usage Widget.
#
# Signing material stays outside the repository (see docs/BUILDING.md):
#   - AIUW_RELEASE_STORE_FILE / AIUW_RELEASE_STORE_PASSWORD / AIUW_RELEASE_KEY_ALIAS /
#     AIUW_RELEASE_KEY_PASSWORD environment variables, or
#   - a properties file (default ~/.android/aiusagewidget-release.properties, override with
#     AIUW_RELEASE_PROPERTIES) with storeFile / storePassword / keyAlias / keyPassword.
# This script never prints passwords or key material.
#
# Output: dist/AIUsageWidget-<version>.apk plus a .sha256 file next to it.
# Pass --skip-tests to skip the JVM unit tests.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROPS="${AIUW_RELEASE_PROPERTIES:-$HOME/.android/aiusagewidget-release.properties}"
BUILD_TOOLS_VERSION="37.0.0"
RUN_TESTS=1
[[ "${1:-}" == "--skip-tests" ]] && RUN_TESTS=0

die() { echo "ERROR: $*" >&2; exit 1; }

# ── preflight ────────────────────────────────────────────────────────────────
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK" ]] || die "ANDROID_HOME (or ANDROID_SDK_ROOT) is not set"
APKSIGNER="$SDK/build-tools/$BUILD_TOOLS_VERSION/apksigner"
[[ -x "$APKSIGNER" ]] || die "apksigner not found at $APKSIGNER"

if [[ -z "${AIUW_RELEASE_STORE_FILE:-}" ]]; then
  [[ -f "$PROPS" ]] || die "signing properties not found at $PROPS (and AIUW_RELEASE_STORE_FILE unset)"
  store_file=$(grep -E '^storeFile=' "$PROPS" | head -1 | cut -d= -f2-)
else
  store_file="$AIUW_RELEASE_STORE_FILE"
fi
[[ -f "$store_file" ]] || die "keystore file not found: $store_file"

version=$(grep -E '^val appVersionName = "' "$PROJECT_ROOT/app/build.gradle.kts" | head -1 | cut -d'"' -f2)
[[ -n "$version" ]] || die "could not read appVersionName from app/build.gradle.kts"

# ── build ────────────────────────────────────────────────────────────────────
cd "$PROJECT_ROOT"
tasks=(clean)
[[ $RUN_TESTS -eq 1 ]] && tasks+=(:app:testDebugUnitTest)
tasks+=(:app:assembleRelease)
./gradlew --no-daemon "${tasks[@]}"

apk_path=$(find app/build/outputs/apk/release -name '*.apk' | head -1)
[[ -n "$apk_path" ]] || die "no release APK produced"

mkdir -p dist
dest="dist/AIUsageWidget-${version}.apk"
cp "$apk_path" "$dest"

# ── verify ───────────────────────────────────────────────────────────────────
echo "→ Verifying signature"
"$APKSIGNER" verify --verbose --print-certs "$dest" \
  | grep -E '^Verified using|Number of signers|Signer.*certificate (DN|SHA-256 digest)'

(cd dist && sha256sum "$(basename "$dest")" > "$(basename "$dest").sha256")
echo
echo "✓ Release APK: $dest"
echo "  SHA-256: $(cut -d' ' -f1 "$dest.sha256")"
echo "  Size:    $(du -h "$dest" | cut -f1)"
