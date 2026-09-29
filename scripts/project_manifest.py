#!/usr/bin/env python3
"""Create or verify the tracked-source SHA-256 manifest."""

from __future__ import annotations

import argparse
import hashlib
import subprocess
import sys
from pathlib import Path

MANIFEST = "PROJECT_MANIFEST.sha256"


def tracked(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True
    )
    return sorted(
        item.decode("utf-8")
        for item in result.stdout.split(b"\0")
        if item and item.decode("utf-8") != MANIFEST
    )


def digest(path: Path) -> str:
    value = hashlib.sha256()
    data = path.read_bytes()
    # Git may materialize UTF-8 text with CRLF on Windows and LF on Linux.
    # The source-integrity manifest describes logical source content, so text
    # line endings are normalized while binary fixtures remain byte-exact.
    if b"\0" not in data:
        try:
            data.decode("utf-8")
        except UnicodeDecodeError:
            pass
        else:
            data = data.replace(b"\r\n", b"\n")
    value.update(data)
    return value.hexdigest()


def render(root: Path) -> str:
    return "".join(
        f"{digest(root / name)}  {name.replace(chr(92), '/')}\n"
        for name in tracked(root)
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true")
    group.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    path = root / MANIFEST
    expected = render(root)
    if args.write:
        path.write_text(expected, encoding="utf-8", newline="\n")
        print(f"WROTE: {path} ({len(tracked(root))} files)")
        return 0
    if not path.is_file() or path.read_text(encoding="utf-8") != expected:
        print(f"ERROR: {MANIFEST} is missing or stale", file=sys.stderr)
        return 1
    print(f"PASS: {MANIFEST} ({len(tracked(root))} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
