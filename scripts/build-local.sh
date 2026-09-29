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
# Build the WAR with Python's standard ZIP writer instead of `jar --date`.
# `jar --date` is unavailable in the Java 11 toolchain that this stock-SageTV
# plugin supports, while the Python path retains sorted entries, a fixed UTC
# timestamp, stable permissions, and byte-identical output across JDK versions.
python3 - "$BUILD/war" "$ROOT/dist/SageTVWebPlayer.war" "$SOURCE_DATE_EPOCH" <<'PY'
from pathlib import Path
import time
import zipfile
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
epoch = max(int(sys.argv[3]), 315532800)  # ZIP timestamps begin in 1980.
timestamp = time.gmtime(epoch)[:6]

with zipfile.ZipFile(
    target,
    "w",
    compression=zipfile.ZIP_DEFLATED,
    compresslevel=9,
) as archive:
    for path in sorted(item for item in source.rglob("*") if item.is_file()):
        entry = zipfile.ZipInfo(path.relative_to(source).as_posix(), timestamp)
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o100644 << 16
        archive.writestr(entry, path.read_bytes())
PY
echo "Built $ROOT/dist/SageTVWebPlayer.war"
