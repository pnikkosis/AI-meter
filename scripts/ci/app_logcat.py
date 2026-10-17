#!/usr/bin/env python3
"""Prints the emulator's log lines that belong to the app, with anything code- or token-like masked.

Usage: app_logcat.py <package>

Reports how many values were masked: a non-zero count means the app (or the system on its behalf)
logged something that looks like a credential, which is itself a finding.
"""
import re
import subprocess
import sys

PKG = sys.argv[1]
SENSITIVE = [
    re.compile(r"(?i)(code|state|token|verifier|challenge|password|secret)=[^&\s]+"),
    re.compile(r"(?i)bearer\s+\S+"),
    re.compile(r"\b[A-Z0-9]{4}-[A-Z0-9]{4,5}\b"),  # device-code style user codes
    re.compile(r"\beyJ[A-Za-z0-9_-]{10,}"),  # JWTs
]

pids = set(subprocess.run(["adb", "shell", "pidof", PKG], capture_output=True, text=True).stdout.split())
log = subprocess.run(["adb", "logcat", "-d", "-b", "main,system,crash", "-v", "threadtime"], capture_output=True, text=True).stdout
masked = 0
lines = []
for line in log.splitlines():
    cols = line.split()
    if not ((len(cols) > 2 and cols[2] in pids) or PKG in line):
        continue
    for pattern in SENSITIVE:
        line, count = pattern.subn("<masked>", line)
        masked += count
    lines.append(line)
print("\n".join(lines[-200:]))
print(f"app log lines: {len(lines)}, masked values: {masked}")
