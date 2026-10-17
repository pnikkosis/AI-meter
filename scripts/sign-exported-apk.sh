#!/usr/bin/env bash
# Signs an UNSIGNED release APK built elsewhere (for example by the "Export unsigned release"
# workflow) with the release key that lives outside the repository, then verifies it.
#
# Usage: scripts/sign-exported-apk.sh <app-release-unsigned.apk> <apksigner.jar> <expected-sha256>
#
#   <expected-sha256>  SHA-256 of the unsigned APK as printed by the build that produced it; the
#                      script refuses to sign a file that does not match.
#
# Signing material is read like scripts/build-release.sh does: the properties file at
# AIUW_RELEASE_PROPERTIES (default ~/.android/aiusagewidget-release.properties) with storeFile /
# storePassword / keyAlias / keyPassword. Passwords are handed to apksigner through environment
# variables, never on the command line, and are never printed.
#
# Output: dist/AIUsageWidget-<version>.apk plus a .sha256 file next to it.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROPS="${AIUW_RELEASE_PROPERTIES:-$HOME/.android/aiusagewidget-release.properties}"

die() { echo "ERROR: $*" >&2; exit 1; }

[[ $# -eq 3 ]] || die "usage: $0 <app-release-unsigned.apk> <apksigner.jar> <expected-sha256>"
unsigned="$1"
apksigner_jar="$2"
expected_sha256="$(echo "$3" | tr 'A-F' 'a-f')"

[[ -f "$unsigned" ]] || die "unsigned APK not found: $unsigned"
[[ -f "$apksigner_jar" ]] || die "apksigner.jar not found: $apksigner_jar"
[[ -f "$PROPS" ]] || die "signing properties not found at $PROPS"
command -v java > /dev/null || die "java is not on PATH"

actual_sha256="$(sha256sum "$unsigned" | cut -d' ' -f1)"
[[ "$actual_sha256" == "$expected_sha256" ]] || die "unsigned APK SHA-256 is $actual_sha256, expected $expected_sha256"

prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2-; }
store_file="$(prop storeFile)"
key_alias="$(prop keyAlias)"
[[ -f "$store_file" ]] || die "keystore file not found: $store_file"
[[ -n "$key_alias" ]] || die "keyAlias missing in $PROPS"

version=$(grep -E '^val appVersionName = "' "$PROJECT_ROOT/app/build.gradle.kts" | head -1 | cut -d'"' -f2)
[[ -n "$version" ]] || die "could not read appVersionName from app/build.gradle.kts"

mkdir -p "$PROJECT_ROOT/dist"
dest="$PROJECT_ROOT/dist/AIUsageWidget-${version}.apk"
rm -f "$dest" "$dest.idsig"

# v2 + v3 signatures (minSdk 31 needs no v1 JAR signature).
AIUW_STORE_PASS="$(prop storePassword)" AIUW_KEY_PASS="$(prop keyPassword)" \
  java -jar "$apksigner_jar" sign \
    --ks "$store_file" --ks-type PKCS12 --ks-key-alias "$key_alias" \
    --ks-pass env:AIUW_STORE_PASS --key-pass env:AIUW_KEY_PASS \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --v4-signing-enabled false \
    --out "$dest" "$unsigned"

echo "→ Verifying signature"
java -jar "$apksigner_jar" verify --verbose --print-certs "$dest" \
  | grep -E '^Verifies|^Verified using|Number of signers|Signer.*certificate (DN|SHA-256 digest)'

(cd "$PROJECT_ROOT/dist" && sha256sum "$(basename "$dest")" > "$(basename "$dest").sha256")
echo
echo "✓ Signed release APK: $dest"
echo "  SHA-256: $(cut -d' ' -f1 "$dest.sha256")"
echo "  Size:    $(du -h "$dest" | cut -f1)"
