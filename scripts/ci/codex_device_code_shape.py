#!/usr/bin/env python3
"""Shows the FORMAT of auth.openai.com's answer to a Codex device-code request, never its values.

Usage: codex_device_code_shape.py

Sends the same request as the app (CodexDeviceCodeClient: endpoint, JSON body, Accept and
User-Agent headers) and prints the HTTP status, a few response headers and every JSON field with
its value reduced to a pattern: digits become 9 and letters become "a", except T and Z in the
timestamp fields (names ending in "_at"), which are kept to show the date format.
The user code is never printed or approved; the pending request expires unused.
"""
import json
import re
import urllib.error
import urllib.request

URL = "https://auth.openai.com/api/accounts/deviceauth/usercode"
BODY = {
    "client_id": "app_EMoamEEZ73f0CkXaXp7hrann",
    "scope": "openid profile email offline_access",
    "redirect_uri": "https://auth.openai.com/deviceauth/callback",
}
HEADERS = {
    "Accept": "application/json",
    "Content-Type": "application/json; charset=utf-8",
    "User-Agent": "AIUsageWidget/1.0.0 (Android)",
}


def pattern(key, value):
    if isinstance(value, dict):
        return "{" + ", ".join(f"{k}: {pattern(k, v)}" for k, v in value.items()) + "}"
    if isinstance(value, list):
        return f"list of {len(value)}"
    if isinstance(value, bool) or value is None:
        return str(value)
    if isinstance(value, (int, float)):
        return type(value).__name__
    masked = re.sub(r"[0-9]", "9", value)
    keep = "TZ" if key.endswith("_at") else ""
    masked = re.sub(rf"[^\W\d_{keep}]", "a", masked)
    return f'string "{masked}"'


def main():
    request = urllib.request.Request(URL, data=json.dumps(BODY).encode(), headers=HEADERS, method="POST")
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    body = response.read()
    print(f"HTTP {response.status}")
    for name in ("Content-Type", "cf-mitigated", "Server"):
        print(f"{name}: {response.headers.get(name)}")
    try:
        answer = json.loads(body)
    except ValueError:
        print(f"body is not JSON ({len(body)} bytes)")
        return
    if isinstance(answer, dict):
        for key, value in answer.items():
            print(f"  {key}: {pattern(key, value)}")
    else:
        print(f"JSON {type(answer).__name__}")


if __name__ == "__main__":
    main()
