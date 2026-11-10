#!/usr/bin/env bash
set -euo pipefail

# Run this from anywhere; resolve files relative to this script.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JUNIT_JAR="$ROOT/lib/junit-platform-console-standalone-1.10.0.jar"
BUILD="$ROOT/build"
mkdir -p "$ROOT/lib" "$BUILD/classes" "$BUILD/test-classes"

if [[ ! -s "$JUNIT_JAR" ]]; then
    echo "Downloading JUnit 5 standalone console..."
    TMP="$JUNIT_JAR.tmp"
    trap 'rm -f "$TMP"' EXIT
    curl --fail --location --retry 2 --output "$TMP" \
      "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar"
    mv "$TMP" "$JUNIT_JAR"
    trap - EXIT
fi

if [[ ! -f "$ROOT/lib/jline.jar" ]]; then
    echo "Missing $ROOT/lib/jline.jar (copy your existing JLine library here)." >&2
    exit 1
fi

# Clear stale classes to avoid accidental Java-version mixing.
rm -rf "$BUILD/classes" "$BUILD/test-classes"
mkdir -p "$BUILD/classes" "$BUILD/test-classes"

echo "Compiling game source for Java 17..."
find "$ROOT/src" -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$ROOT/lib/*" -d "$BUILD/classes"

echo "Compiling JUnit tests for Java 17..."
find "$ROOT/test" -name '*.java' -print0 | xargs -0 javac --release 17 \
  -cp "$BUILD/classes:$ROOT/lib/*" -d "$BUILD/test-classes"

echo "Running JUnit 5 suite..."
java -jar "$JUNIT_JAR" execute \
    --class-path "$BUILD/classes:$BUILD/test-classes:$ROOT/lib/jline.jar" \
    --scan-class-path "$BUILD/test-classes" \
    --fail-if-no-tests
