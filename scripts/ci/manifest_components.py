#!/usr/bin/env python3
"""Lists the components of a merged manifest with their exported flag and permission.

Usage: aapt2 dump xmltree --file AndroidManifest.xml app.apk | manifest_components.py
"""
import re
import sys

KINDS = ("activity", "activity-alias", "provider", "receiver", "service")
ATTRIBUTE = re.compile(r"\s*A: http://schemas.android.com/apk/res/android:(name|exported|permission)\(0x[0-9a-f]+\)=(.*)")
current = None


def flush():
    if current:
        kind, attrs = current
        print(f"{kind:9} {attrs.get('name', '?')} exported={attrs.get('exported', '(default)')} "
              f"permission={attrs.get('permission', '-')}")


for line in sys.stdin:
    element = re.match(r"\s*E: ([\w-]+) ", line)
    if element:
        if element.group(1) in KINDS:
            flush()
            current = (element.group(1), {})
        elif element.group(1) in ("application", "manifest"):
            flush()
            current = None
        continue
    attribute = ATTRIBUTE.match(line)
    # Only the component's own attributes: they come before any nested element's attributes.
    if current is not None and attribute and attribute.group(1) not in current[1]:
        current[1][attribute.group(1)] = attribute.group(2).split(" (Raw")[0].strip('"')
flush()
