#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$JAVA_BIN" -version 2>&1 | head -1 | grep -q '25'; then
    echo "Java 25 is required to run Gradle for this project."
    exit 1
fi

./gradlew :mc12111:clean :mc263:clean :mc12111:build :mc263:build

mkdir -p dist
cp mc12111/build/libs/yuri-addons-1.21.11-1.0.0.jar dist/
cp mc263/build/libs/yuri-addons-26.3-1.0.0.jar dist/
