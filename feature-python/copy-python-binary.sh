#!/bin/bash
# Copy python3 binary from jniLibs to assets for each ABI

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNILIBS_DIR="$PROJECT_ROOT/impl/src/main/jniLibs"
ASSETS_DIR="$PROJECT_ROOT/src/main/assets/python3.14/bin"

echo "Copying python3 binaries from jniLibs to assets..."

# Create destination directory
mkdir -p "$ASSETS_DIR"

# Copy for each ABI
for abi in arm64-v8a x86_64; do
    src="$JNILIBS_DIR/$abi/prefix/bin/python3"
    dst="$ASSETS_DIR/python3.$abi"
    
    if [ -f "$src" ]; then
        cp "$src" "$dst"
        chmod +x "$dst"
        echo "✓ Copied $abi: $dst"
    else
        echo "✗ Not found: $src"
    fi
done

echo "Done!"
