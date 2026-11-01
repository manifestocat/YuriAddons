#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

JAVA_25="${JAVA_HOME_25:-}"
if [[ -z "$JAVA_25" && -x /usr/libexec/java_home ]]; then
    JAVA_25="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
fi

./gradlew clean build

if [[ -z "$JAVA_25" ]]; then
    echo "Set JAVA_HOME_25 to a JDK 25 installation to build the 26.3 jar."
    exit 1
fi

JAVA_HOME="$JAVA_25" ./gradlew -p mc263 clean build

mkdir -p dist
cp build/libs/yuri-addons-1.21.11-1.0.0.jar dist/
cp mc263/build/libs/yuri-addons-26.3-1.0.0.jar dist/
