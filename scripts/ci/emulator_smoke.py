#!/usr/bin/env python3
"""Drives an installed build of the app on an emulator and records what it shows.

Usage: emulator_smoke.py <apk> <package> <locale> <out_dir>

Installs <apk>, launches it (the emulator's system locale is <locale>), walks the main screens,
starts a Claude sign-in and probes its loopback receiver from outside the app with fake values,
starts a Codex device-code sign-in and cancels it, and saves screenshots to <out_dir>.

Nothing secret is printed: no credentials exist on the emulator, the Claude state never leaves the
app, and the Codex user code is only reported as shown/not shown (no screenshot while it is visible).
Exit status 1 when the app crashes or a screen that needs no network is not reached; steps that
depend on the providers' servers are reported but do not fail the run.
"""
import http.client
import ipaddress
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, PKG, LOCALE, OUT = sys.argv[1:5]
ACTIVITY = f"{PKG}/com.kmnexus.codexmeter.MainActivity"
FORWARD_PORT = 18765
failures = []
step = 0


def adb(*args, check=True, timeout=120):
    result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def shot(name):
    global step
    step += 1
    path = os.path.join(OUT, f"{step:02d}-{name}.png")
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
    with open(path, "wb") as out:
        out.write(data)
    print(f"  screenshot {os.path.basename(path)} ({len(data)} bytes)")


def nodes():
    out = ""
    for _ in range(4):
        out = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        if "dumped to" in out:
            xml = adb("shell", "cat", "/sdcard/ui.xml")
            return list(ET.fromstring(xml[xml.index("<"):]).iter("node"))
        time.sleep(2)
    print(f"  uiautomator dump failed: {out.strip()}")
    return []


def screen_height():
    sizes = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return int(sizes[-1][1]) if sizes else 2000


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]"))))


def find(labels, timeout=15):
    deadline = time.time() + timeout
    while True:
        for node in nodes():
            for attr in ("text", "content-desc"):
                if (node.get(attr) or "").strip() in labels:
                    return node
        if time.time() > deadline:
            return None
        time.sleep(1)


def tap_label(labels, what, required=True, timeout=15, scroll=True):
    """Taps the first node showing one of `labels`, scrolling down when it is below the screen."""
    height = screen_height()
    for _ in range(6 if scroll else 1):
        node = find(labels, timeout=timeout)
        if node is not None:
            x1, y1, x2, y2 = bounds(node)
            if y2 > y1 and y2 <= height - 40:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(2)
                print(f"  tapped {what}")
                return True
        if scroll:
            adb("shell", "input", "swipe", "360", str(int(height * 0.75)), "360", str(int(height * 0.3)), "300")
            time.sleep(1)
        timeout = 3
    message = f"{what}: none of {labels} reachable on screen"
    print(f"  {message}")
    if required:
        failures.append(message)
    return False


def visible(labels):
    return find(labels, timeout=2) is not None


def alive():
    return adb("shell", "pidof", PKG, check=False).strip() != ""


def app_uid():
    match = re.search(r"userId=(\d+)", adb("shell", "dumpsys", "package", PKG))
    return int(match.group(1)) if match else None


def decode_address(hex_address):
    raw = bytes.fromhex(hex_address)
    if len(raw) == 4:
        return ipaddress.IPv4Address(raw[::-1])
    address = ipaddress.IPv6Address(b"".join(raw[i:i + 4][::-1] for i in range(0, 16, 4)))
    return address.ipv4_mapped or address


def listening_ports(uid):
    """(address, port) of the TCP sockets the app is listening on, from /proc/net/tcp{,6}."""
    ports = set()
    for table in ("/proc/net/tcp", "/proc/net/tcp6"):
        for line in adb("shell", "cat", table, check=False).splitlines()[1:]:
            cols = line.split()
            if len(cols) > 7 and cols[3] == "0A" and cols[7] == str(uid):
                address, port = cols[1].rsplit(":", 1)
                ports.add((str(decode_address(address)), int(port, 16)))
    return ports


def code_visible():
    return any(re.match(r"^(Código|Code): \S+", node.get("text") or "") for node in nodes())


def request(method, path):
    connection = http.client.HTTPConnection("127.0.0.1", FORWARD_PORT, timeout=10)
    connection.request(method, path)
    response = connection.getresponse()
    body = response.read().decode("utf-8", "replace")
    headers = {k.lower(): v for k, v in response.getheaders()}
    connection.close()
    return response.status, headers, body


def check(condition, message):
    print(f"  {'ok  ' if condition else 'FAIL'} {message}")
    if not condition:
        failures.append(message)


def back_to_app():
    for _ in range(3):
        if visible(["AI Usage Widget", "Inicio", "Home", "Conectar Claude", "Connect Claude"]):
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)


def main():
    os.makedirs(OUT, exist_ok=True)
    # Root (allowed on google_apis images) is only needed to read the app's sockets in /proc/net.
    adb("root", check=False)
    time.sleep(3)
    adb("wait-for-device")
    print("== install")
    print("  " + adb("install", "-r", APK).strip().splitlines()[-1])
    print(f"  system locale: {adb('shell', 'getprop', 'persist.sys.locale').strip() or 'default'}")
    adb("logcat", "-c")
    uid = app_uid()

    es = LOCALE.startswith("es")
    add_account = ["Agregar cuenta"] if es else ["Add account"]
    continue_browser = ["Continuar en el navegador"] if es else ["Continue in browser"]
    cancel_sign_in = ["Cancelar inicio de sesión"] if es else ["Cancel sign-in"]
    settings_tab = ["Ajustes", "Pestaña Ajustes"] if es else ["Settings", "Settings tab"]
    accounts_tab = ["Cuentas", "Pestaña Cuentas"] if es else ["Account", "Account tab"]
    home_tab = ["Inicio", "Pestaña Inicio"] if es else ["Home", "Home tab"]

    print("== launch")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(6)
    check(alive(), "app process is running after launch")
    shot("home")

    print("== accounts tab")
    tap_label(accounts_tab, "accounts tab")
    shot("accounts")

    print("== settings tab")
    if tap_label(settings_tab, "settings tab"):
        shot("settings")
        height = screen_height()
        for _ in range(8):
            adb("shell", "input", "swipe", "360", str(int(height * 0.8)), "360", str(int(height * 0.2)), "200")
        time.sleep(1)
        shot("settings-bottom")

    print("== add account → providers")
    tap_label(home_tab, "home tab")
    tap_label(add_account, "add account")
    shot("providers")

    print("== Claude sign-in")
    if tap_label(["Claude"], "Claude provider"):
        shot("claude-disclosure")
        listening_before = listening_ports(uid) if uid else set()
        if tap_label(continue_browser, "continue in browser"):
            time.sleep(6)
            # A fresh emulator shows Chrome's first-run screens before the custom tab.
            for labels in (["Usar sin una cuenta", "Use without an account"], ["No, gracias", "No thanks"]):
                tap_label(labels, "Chrome first-run screen", required=False, timeout=5, scroll=False)
            time.sleep(8)
            top = [line.strip() for line in adb("shell", "dumpsys", "activity", "activities").splitlines()
                   if "topResumedActivity" in line or "mResumedActivity" in line]
            print(f"  resumed activity: {top[:1]}")
            shot("claude-browser")
            ports = (listening_ports(uid) - listening_before) if uid else set()
            print(f"  new listening sockets of the app: {sorted(ports)}")
            no_browser = visible([
                "No hay un navegador disponible para abrir la página de inicio de sesión de Claude.",
                "No browser is available to open the Claude sign-in page.",
            ])
            if no_browser:
                print("  the emulator has no browser for Custom Tabs: receiver checks skipped")
            else:
                check(bool(ports), "a loopback receiver is listening while the sign-in waits")
                check(all(ipaddress.ip_address(a).is_loopback for a, _ in ports),
                      "the receiver listens only on loopback addresses")
            if ports:
                port = sorted(ports)[0][1]
                adb("forward", f"tcp:{FORWARD_PORT}", f"tcp:{port}")
                status, headers, body = request("GET", "/callback?code=probe-code&state=wrong-state")
                check(status == 400, f"wrong state is refused (HTTP {status})")
                check("probe-code" not in body, "the response never echoes the code")
                check(headers.get("cache-control", "").startswith("no-store"), "responses are no-store")
                check("default-src 'none'" in headers.get("content-security-policy", ""), "responses carry a CSP")
                status, _, _ = request("GET", "/callback?code=a&code=b&state=x")
                check(status == 400, f"duplicated parameters are refused (HTTP {status})")
                status, _, _ = request("POST", "/callback")
                check(status == 405, f"POST is refused (HTTP {status})")
                status, _, _ = request("GET", "/favicon.ico")
                check(status == 404, f"other paths are refused (HTTP {status})")
                still = listening_ports(uid) - listening_before
                check(bool(still), "forged callbacks do not end the sign-in attempt")
                adb("forward", "--remove", f"tcp:{FORWARD_PORT}", check=False)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(3)
            back_to_app()
            shot("claude-waiting")
            if tap_label(cancel_sign_in, "cancel Claude sign-in"):
                time.sleep(2)
                left = (listening_ports(uid) - listening_before) if uid else set()
                check(not left, "cancelling closes the loopback receiver")
                shot("claude-cancelled")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)

    print("== Codex sign-in")
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2)
    if not visible(["Codex"]):
        tap_label(home_tab, "home tab", required=False)
        tap_label(add_account, "add account", required=False)
    if tap_label(["Codex"], "Codex provider"):
        # The screen asks for a device code as soon as it opens. The user code is an authorization
        # code: no screenshot is taken while a sign-in may be showing one, only whether it appeared.
        time.sleep(12)
        print(f"  Codex device code shown: {'yes (not captured)' if code_visible() else 'no'}")
        tap_label(cancel_sign_in, "cancel Codex sign-in", required=False)
        time.sleep(3)
        if code_visible():
            print("  a code is still on screen after cancelling: no screenshot")
        else:
            shot("codex-cancelled")

    print("== crashes")
    check(alive(), "app process is still running at the end")
    crash_log = adb("logcat", "-d", "-b", "crash", check=False)
    crash_lines = [line for line in crash_log.splitlines() if PKG in line or "FATAL" in line]
    check(not crash_lines, "no crash recorded for the app")
    for line in crash_lines[:40]:
        print(f"    {line}")

    print("== result")
    if failures:
        print(f"SMOKE: {len(failures)} problem(s)")
        for message in failures:
            print(f"  - {message}")
        sys.exit(1)
    print("SMOKE: OK")


if __name__ == "__main__":
    main()
