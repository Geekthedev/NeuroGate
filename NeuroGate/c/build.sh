#!/bin/bash

# NeuroCore C Build Script

# Create build directory if it doesn't exist
mkdir -p ../build/lib

# Set compiler flags
CFLAGS="-Wall -Wextra -O2 -fPIC"
LDFLAGS="-shared"

# Source files
CORE_SRC="core/neuron.c core/synapse.c"
MEMORY_SRC="memory/mm.c"
UTILS_SRC="utils/log.c"
CRYPTO_SRC="crypto/hash.c"
NET_SRC="net/transport.c"
API_SRC="api/bridge.c"
RUNTIME_SRC="runtime/exec.c"

ALL_SRC="$CORE_SRC $MEMORY_SRC $UTILS_SRC $CRYPTO_SRC $NET_SRC $API_SRC $RUNTIME_SRC"

# Build shared library
echo "Building NeuroCore shared library..."
gcc $CFLAGS -I. $ALL_SRC $LDFLAGS -o ../build/lib/libneurocore.so

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "Library created at ../build/lib/libneurocore.so"
else
    echo "Build failed!"
    exit 1
fi

# Copy header files to include directory
echo "Copying header files..."
mkdir -p ../build/include/neurocore
find . -name "*.h" -exec cp --parents {} ../build/include/neurocore \;

echo "Done!"