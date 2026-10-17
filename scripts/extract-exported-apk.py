#!/usr/bin/env python3
"""Rebuilds the files printed by the "Export unsigned release (manual)" workflow from its job logs.

Usage: python3 -I scripts/extract-exported-apk.py <out_dir> <job_log> [<job_log> ...]

Pass the logs of the jobs "Print part 0" to "Print part 8" (plain text as GitHub serves them, or the
JSON {"logs_content": ...} some API clients save). Writes <out_dir>/apksigner.jar and
<out_dir>/app-release-unsigned.apk, checks every slice against the SHA-256 its job printed and shows
the SHA-256 of both files, to compare with the "Describe outputs" step of the build job.
The logs are only decoded, never executed.
"""
import base64
import hashlib
import json
import os
import re
import sys

PARTS = 8
TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z ?")
SLICE_LINE = re.compile(r"slice=(\d+) of \d+, bytes=\d+, sha256=([0-9a-f]{64})")


def log_lines(path):
    raw = open(path, encoding="utf-8", errors="replace").read()
    try:
        raw = json.loads(raw)["logs_content"]
    except (ValueError, KeyError, TypeError):
        pass
    for line in raw.split("\n"):
        yield TIMESTAMP.sub("", line.lstrip("﻿")).strip().lstrip("﻿")


def read_blocks(paths):
    blocks, slice_sha = {}, {}
    for path in paths:
        current, buf = None, []
        for line in log_lines(path):
            match = SLICE_LINE.search(line)
            if match:
                slice_sha[int(match.group(1))] = match.group(2)
            begin = re.fullmatch(r"=====BEGIN (\S+)=====", line)
            if begin:
                current, buf = begin.group(1), []
            elif current and line == f"=====END {current}=====":
                blocks[current] = base64.b64decode("".join(buf), validate=True)
                current = None
            elif current and line:
                buf.append(line)
    return blocks, slice_sha


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out_dir = sys.argv[1]
    blocks, slice_sha = read_blocks(sys.argv[2:])
    missing = [f"apk.part{n}" for n in range(1, PARTS + 1) if f"apk.part{n}" not in blocks]
    if "apksigner.jar" not in blocks:
        missing.insert(0, "apksigner.jar")
    if missing:
        sys.exit("missing in the logs: " + ", ".join(missing))
    for n in range(1, PARTS + 1):
        actual = hashlib.sha256(blocks[f"apk.part{n}"]).hexdigest()
        if slice_sha.get(n) not in (None, actual):
            sys.exit(f"slice {n} does not match the SHA-256 printed by its job")
    os.makedirs(out_dir, exist_ok=True)
    files = {
        "apksigner.jar": blocks["apksigner.jar"],
        "app-release-unsigned.apk": b"".join(blocks[f"apk.part{n}"] for n in range(1, PARTS + 1)),
    }
    for name, data in files.items():
        with open(os.path.join(out_dir, name), "wb") as out:
            out.write(data)
        print(f"{hashlib.sha256(data).hexdigest()}  {name} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
