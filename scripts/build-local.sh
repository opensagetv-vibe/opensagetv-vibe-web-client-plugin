#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/build/manual"
SERVLET_JAR="${SERVLET_API_JAR:-/usr/share/java/servlet-api.jar}"
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(sed -n 's/^SOURCE_DATE_EPOCH=//p' "$ROOT/release.properties" | head -n 1)}"
if [[ ! -f "$SERVLET_JAR" ]]; then
  echo "Set SERVLET_API_JAR to a javax.servlet API 3.1+ jar." >&2
  exit 2
fi
rm -rf "$BUILD"
mkdir -p "$ROOT/dist" "$BUILD/classes" "$BUILD/war/WEB-INF/classes"
javac --release 8 -cp "$SERVLET_JAR" -d "$BUILD/classes" $(find "$ROOT/src/main/java" -name '*.java')
cp -a "$ROOT/src/main/webapp/." "$BUILD/war/"
cp -a "$BUILD/classes/." "$BUILD/war/WEB-INF/classes/"
if [[ ! "$SOURCE_DATE_EPOCH" =~ ^[0-9]+$ ]]; then
  echo "SOURCE_DATE_EPOCH must be an integer Unix timestamp." >&2
  exit 2
fi
ZIP_DATE="$(date -u -d "@$SOURCE_DATE_EPOCH" '+%Y-%m-%dT%H:%M:%SZ')"
# jar's explicit timestamp normalizes both generated class and copied resource
# entries. The JDK sorts a recursively added tree, yielding a byte-identical WAR
# for an unchanged source tree and build environment.
(cd "$BUILD/war" && jar --create --file "$ROOT/dist/SageTVWebPlayer.war" --date="$ZIP_DATE" .)
echo "Built $ROOT/dist/SageTVWebPlayer.war"
