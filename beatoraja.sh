#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "JDK 26 not found at $JAVA_HOME" >&2
    exit 1
fi

JAR_PATH="$SCRIPT_DIR/build/libs/beatoraja.jar"
if [[ ! -f "$JAR_PATH" ]]; then
    echo "Missing $JAR_PATH" >&2
    echo "Build it first with: gradle build" >&2
    exit 1
fi

mkdir -p table

# Workaround for NVIDIA EGL bug on Wayland (glfw/glfw#2680)
export __GL_THREADED_OPTIMIZATIONS=0

exec "$JAVA_HOME/bin/java" \
    --enable-native-access=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow \
    -Xms1g -Xmx4g \
    -jar "$JAR_PATH" \
    "$@"
