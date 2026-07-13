#!/bin/bash

# Download verified Windows and Android packages from the current GitHub release.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/app/king-detective}"
DOWNLOAD_DIR="${CLIENT_DOWNLOAD_DIR:-$APP_DIR/deploy/downloads}"
REPOSITORY="${KING_DETECTIVE_GITHUB_REPOSITORY:-tony-wang1990/Wang-Detective-2}"
RELEASE_TAG="${CLIENT_PACKAGE_RELEASE_TAG:-latest}"

case "$REPOSITORY" in
    *[!A-Za-z0-9_.\/-]*|*/*/*|/*|*/|"")
        echo "[ERROR] Invalid GitHub repository: $REPOSITORY" >&2
        exit 1
        ;;
esac

if [ "$RELEASE_TAG" = "latest" ]; then
    RELEASE_BASE_URL="https://github.com/${REPOSITORY}/releases/latest/download"
else
    case "$RELEASE_TAG" in
        *[!A-Za-z0-9._-]*|"")
            echo "[ERROR] Invalid client package release tag: $RELEASE_TAG" >&2
            exit 1
            ;;
    esac
    RELEASE_BASE_URL="https://github.com/${REPOSITORY}/releases/download/${RELEASE_TAG}"
fi

if command -v curl >/dev/null 2>&1; then
    download() {
        curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 1800 \
            -o "$2" "$1"
    }
elif command -v wget >/dev/null 2>&1; then
    download() {
        wget -q --timeout=30 --tries=3 -O "$2" "$1"
    }
else
    echo "[ERROR] curl or wget is required" >&2
    exit 1
fi

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print tolower($1)}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print tolower($1)}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "$1" | awk '{print tolower($NF)}'
    else
        echo "[ERROR] No SHA256 utility is available" >&2
        return 1
    fi
}

sync_package() {
    file_name="$1"
    target="$DOWNLOAD_DIR/$file_name"
    sidecar="$target.sha256"
    temp_target="$target.tmp.$$"
    temp_sidecar="$sidecar.tmp.$$"

    rm -f "$temp_target" "$temp_sidecar"
    echo "Downloading checksum: $file_name.sha256"
    download "$RELEASE_BASE_URL/$file_name.sha256" "$temp_sidecar"

    expected_sha="$(awk 'NR == 1 {print tolower($1)}' "$temp_sidecar")"
    if ! printf '%s' "$expected_sha" | grep -Eq '^[0-9a-f]{64}$'; then
        rm -f "$temp_target" "$temp_sidecar"
        echo "[ERROR] Invalid checksum sidecar for $file_name" >&2
        return 1
    fi

    if [ -f "$target" ] && [ "$(sha256_file "$target")" = "$expected_sha" ]; then
        mv "$temp_sidecar" "$sidecar"
        echo "Package already current: $file_name"
        return 0
    fi

    echo "Downloading package: $file_name"
    download "$RELEASE_BASE_URL/$file_name" "$temp_target"
    actual_sha="$(sha256_file "$temp_target")"
    if [ "$actual_sha" != "$expected_sha" ]; then
        rm -f "$temp_target" "$temp_sidecar"
        echo "[ERROR] SHA256 mismatch for $file_name" >&2
        return 1
    fi

    mv "$temp_target" "$target"
    mv "$temp_sidecar" "$sidecar"
    chmod 644 "$target" "$sidecar" 2>/dev/null || true
    echo "Package synchronized: $file_name ($actual_sha)"
}

mkdir -p "$DOWNLOAD_DIR"
sync_package "wang-detective-latest.apk"
sync_package "Wang-Detective-Setup-latest.exe"

echo "Client packages are ready in $DOWNLOAD_DIR"
