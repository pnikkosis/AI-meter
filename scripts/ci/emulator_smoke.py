#!/usr/bin/env python3
"""Drives an installed build of the app on an emulator and records what it shows.

Usage: emulator_smoke.py <apk> <package> <locale> <out_dir>

Installs <apk>, launches it (the emulator's system locale is <locale>), walks the main screens,
starts the Claude and the Codex browser sign-ins and probes their loopback receivers from outside
the app with fake values, starts a Codex device-code sign-in and cancels it, turns the persistent
notification on through Android's permission dialog, and saves screenshots to <out_dir>.

Nothing secret is printed: no credentials exist on the emulator, the sign-in states never leave the
app, page texts with query strings are left out, and the Codex user code is only reported as
shown/not shown (no screenshot while it is visible).
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


def dump():
    out = ""
    for _ in range(4):
        out = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        if "dumped to" in out:
            xml = adb("shell", "cat", "/sdcard/ui.xml")
            return list(ET.fromstring(xml[xml.index("<"):]).iter("node"))
        time.sleep(2)
    print(f"  uiautomator dump failed: {out.strip()}")
    return []


def nodes():
    """UI nodes on screen, after dismissing the emulator's own "isn't responding" dialogs."""
    found = dump()
    for _ in range(3):
        texts = [(n.get("text") or "") for n in found]
        if not any("isn't responding" in t or "no responde" in t for t in texts):
            return found
        wait = next((n for n in found if (n.get("text") or "").strip() in ("Wait", "Esperar")), None)
        if wait is None:
            return found
        x1, y1, x2, y2 = bounds(wait)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        print("  dismissed a system 'not responding' dialog")
        time.sleep(3)
        found = dump()
    return found


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
            # The middle is what gets tapped: a bottom tab reaches the screen edge on devices without
            # a navigation bar, so its bottom edge alone says nothing about whether it is on screen.
            if y2 > y1 and (y1 + y2) // 2 <= height - 40:
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
    # Android 12 prints userId=, newer releases appId=.
    match = re.search(r"(?:userId|appId)=(\d+)", adb("shell", "dumpsys", "package", PKG))
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


def network_policy(uid):
    """Lines of `dumpsys netpolicy` about the app's uid and the background network restriction."""
    lines = adb("shell", "dumpsys", "netpolicy", check=False).splitlines()
    own = [line.strip()[:300] for line in lines if re.search(rf"(?i)\buid[=: ]+{uid}\b", line)]
    general = [line.strip()[:200] for line in lines if re.search(r"(?i)background", line)]
    return own[:6] + general[:6]


def page_texts(limit=12):
    """Visible texts (for example of the sign-in page in the browser tab), without URLs or queries."""
    texts = []
    for node in nodes():
        text = (node.get("text") or node.get("content-desc") or "").strip()
        if text and "=" not in text and "?" not in text and "/" not in text and text not in texts:
            texts.append(text[:80])
    return texts[:limit]


def notification_permission_granted():
    match = re.search(r"android.permission.POST_NOTIFICATIONS: granted=(true|false)",
                      adb("shell", "dumpsys", "package", PKG, check=False))
    return match is not None and match.group(1) == "true"


def posted_notifications():
    """Number of notifications the app has posted (only counted, never printed)."""
    return sum(1 for line in adb("shell", "dumpsys", "notification", check=False).splitlines()
               if "NotificationRecord(" in line and f"pkg={PKG}" in line)


def switch_on_row(title_labels):
    """The checkable node (the switch) on the same row as the text showing one of title_labels."""
    found = nodes()
    title = next((n for n in found if (n.get("text") or "").strip() in title_labels), None)
    if title is None:
        return None
    _, top, _, bottom = bounds(title)
    for node in found:
        if node.get("checkable") == "true":
            _, y1, _, y2 = bounds(node)
            if y1 <= bottom + 150 and y2 >= top - 150:
                return node
    return None


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
        if visible(["AIMeter", "Inicio", "Home", "Conectar Claude", "Connect Claude",
                    "Conectar Codex", "Connect Codex"]):
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)


def resumed_activity():
    return [line.strip() for line in adb("shell", "dumpsys", "activity", "activities").splitlines()
            if "topResumedActivity" in line or "mResumedActivity" in line][:1]


def app_in_front():
    return any(f"{PKG}/" in line for line in resumed_activity())


def dismiss_chrome_first_run():
    # A fresh emulator shows Chrome's first-run screens before the custom tab.
    for labels in (
        ["Usar sin una cuenta", "Use without an account", "Aceptar y continuar", "Accept & continue"],
        ["No, gracias", "No thanks"],
    ):
        tap_label(labels, "Chrome first-run screen", required=False, timeout=5, scroll=False)


def browser_sign_in(name, uid, continue_browser, reopen_page, cancel_sign_in, callback_path,
                    no_browser_texts, expected_ports=None):
    """From a provider's disclosure screen: opens the sign-in page in the browser, probes the loopback
    receiver from outside the app with fake values, comes back and cancels."""
    listening_before = listening_ports(uid) if uid else set()
    if not tap_label(continue_browser, f"continue in browser ({name})"):
        return
    browser_opened = time.monotonic()
    time.sleep(6)
    dismiss_chrome_first_run()
    time.sleep(8)
    if app_in_front():
        # Chrome's first start on a fresh emulator can outlast the wait above (run 13): ask the app
        # to open the same page once more.
        print("  the browser is not in front yet: reopening the sign-in page")
        tap_label(reopen_page, f"reopen the sign-in page ({name})", required=False)
        time.sleep(6)
        dismiss_chrome_first_run()
        time.sleep(6)
    print(f"  resumed activity: {resumed_activity()}")
    print(f"  page texts: {page_texts()}")
    shot(f"{name}-browser")
    ports = (listening_ports(uid) - listening_before) if uid else set()
    print(f"  new listening sockets of the app: {sorted(ports)}")
    if visible(no_browser_texts):
        print("  the emulator has no browser for Custom Tabs: receiver checks skipped")
    else:
        check(bool(ports), f"a loopback receiver is listening while the {name} sign-in waits")
        check(all(ipaddress.ip_address(a).is_loopback for a, _ in ports),
              "the receiver listens only on loopback addresses")
        if expected_ports:
            check(all(port in expected_ports for _, port in ports),
                  f"the receiver uses one of the registered ports {expected_ports}")
    if ports:
        port = sorted(ports)[0][1]
        adb("forward", f"tcp:{FORWARD_PORT}", f"tcp:{port}")
        status, headers, body = request("GET", f"{callback_path}?code=probe-code&state=wrong-state")
        check(status == 400, f"wrong state is refused (HTTP {status})")
        check("probe-code" not in body, "the response never echoes the code")
        check(headers.get("cache-control", "").startswith("no-store"), "responses are no-store")
        check("default-src 'none'" in headers.get("content-security-policy", ""), "responses carry a CSP")
        status, _, _ = request("GET", f"{callback_path}?code=a&code=b&state=x")
        check(status == 400, f"duplicated parameters are refused (HTTP {status})")
        status, _, _ = request("POST", callback_path)
        check(status == 405, f"POST is refused (HTTP {status})")
        for other in ("/favicon.ico", "/callback" if callback_path != "/callback" else "/auth/callback"):
            status, _, _ = request("GET", other)
            check(status == 404, f"other paths are refused ({other}: HTTP {status})")
        still = listening_ports(uid) - listening_before
        check(bool(still), "forged callbacks do not end the sign-in attempt")
        adb("forward", "--remove", f"tcp:{FORWARD_PORT}", check=False)
    # Android may cut the network of an app left behind another screen (here the browser tab)
    # after up to 20 s; the sign-ins therefore exchange the code only once the app is back.
    time.sleep(max(0.0, 25 - (time.monotonic() - browser_opened)))
    if uid:
        print(f"  network policy while behind the browser tab: {network_policy(uid)}")
    # BACK closes the browser tab; with the app already in front it would leave the sign-in screen.
    if not app_in_front():
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(3)
    back_to_app()
    if uid:
        print(f"  network policy back in front: {network_policy(uid)}")
    shot(f"{name}-waiting")
    if tap_label(cancel_sign_in, f"cancel {name} sign-in"):
        time.sleep(2)
        left = (listening_ports(uid) - listening_before) if uid else set()
        check(not left, "cancelling closes the loopback receiver")
        shot(f"{name}-cancelled")


def device_code_sign_in(es, cancel_sign_in):
    """The Codex device-code screen asks for a code as soon as it opens. The user code is an
    authorization code: no screenshot is taken while one may be showing, only whether it appeared."""
    time.sleep(12)
    shown = code_visible()
    if not shown:
        # Name what the screen reports (an error message, never a code) and check name resolution.
        texts = [n.get("text") for n in nodes() if (n.get("text") or "").strip()]
        texts = [re.sub(r"\b[A-Z0-9]{4}-[A-Z0-9]{4,5}\b", "<masked>", t) for t in texts]
        print(f"  Codex screen says: {texts[:6]}")
        for host in ("auth.openai.com", "chatgpt.com"):
            ping = adb("shell", "ping", "-c", "1", "-W", "5", host, check=False, timeout=30)
            print(f"  ping {host}: {(ping.splitlines() or ['no output'])[0]}")
        start = ["Iniciar sesión en Codex"] if es else ["Start Codex sign-in"]
        if tap_label(start, "retry Codex sign-in", required=False, scroll=False):
            time.sleep(12)
            shown = code_visible()
    print(f"  Codex device code shown: {'yes (not captured)' if shown else 'no'}")
    tap_label(cancel_sign_in, "cancel Codex device-code sign-in", required=False)
    time.sleep(3)
    if code_visible():
        print("  a code is still on screen after cancelling: no screenshot")
    else:
        shot("codex-device-code-cancelled")


def notification_permission(es, settings_tab, sdk):
    """The persistent notification is on by default, but on Android 13+ nothing is shown until the
    permission is granted: Settings must say so, and its switch must ask for the permission."""
    status_title = ["Notificación de estado persistente"] if es else ["Persistent status notification"]
    blocked_hint = (
        ["Android tiene bloqueadas las notificaciones de esta app. Activar el interruptor para permitirlas."]
        if es else ["Android is blocking this app's notifications. Turn the switch on to allow them."]
    )
    # Start from a fresh Home: the sign-in screens before this one have no tab bar.
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(5)
    tap_label(settings_tab, "settings tab")
    height = screen_height()
    for _ in range(6):
        adb("shell", "input", "swipe", "360", str(int(height * 0.3)), "360", str(int(height * 0.8)), "200")
    time.sleep(1)
    granted = notification_permission_granted()
    print(f"  SDK {sdk}, POST_NOTIFICATIONS granted: {granted}, notifications posted: {posted_notifications()}")
    if sdk < 33 or granted:
        check(not visible(blocked_hint), "no blocked-notifications hint while notifications are allowed")
        return
    check(visible(blocked_hint), "Settings says Android blocks the app's notifications")
    switch = switch_on_row(status_title)
    check(switch is not None and switch.get("checked") == "false",
          "the status notification switch reads off while notifications are blocked")
    shot("settings-notifications-blocked")
    if switch is None:
        return
    x1, y1, x2, y2 = bounds(switch)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)
    if not tap_label(["Permitir notificaciones", "Allow notifications"], "allow in the app's explanation",
                     scroll=False):
        return
    time.sleep(2)
    shot("system-permission-dialog")
    tap_label(["Permitir", "Allow"], "allow in Android's permission dialog", scroll=False)
    time.sleep(4)
    check(notification_permission_granted(), "the permission is granted after the dialog")
    check(not visible(blocked_hint), "the blocked-notifications hint is gone")
    switch = switch_on_row(status_title)
    check(switch is not None and switch.get("checked") == "true", "the status notification switch reads on")
    print(f"  notifications posted by the app: {posted_notifications()}")
    shot("settings-notifications-allowed")


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
    reopen_page = ["Abrir otra vez la página de inicio de sesión"] if es else ["Open the sign-in page again"]
    cancel_sign_in = ["Cancelar inicio de sesión"] if es else ["Cancel sign-in"]
    settings_tab = ["Ajustes", "Pestaña Ajustes"] if es else ["Settings", "Settings tab"]
    accounts_tab = ["Cuentas", "Pestaña Cuentas"] if es else ["Accounts", "Accounts tab", "Account", "Account tab"]
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
        browser_sign_in(
            name="claude",
            uid=uid,
            continue_browser=continue_browser,
            reopen_page=reopen_page,
            cancel_sign_in=cancel_sign_in,
            callback_path="/callback",
            no_browser_texts=[
                "No hay un navegador disponible para abrir la página de inicio de sesión de Claude.",
                "No browser is available to open the Claude sign-in page.",
            ],
        )
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)

    print("== Codex sign-in (browser)")
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2)
    if not visible(["Codex"]):
        tap_label(home_tab, "home tab", required=False)
        tap_label(add_account, "add account", required=False)
    if tap_label(["Codex"], "Codex provider"):
        shot("codex-disclosure")
        browser_sign_in(
            name="codex",
            uid=uid,
            continue_browser=continue_browser,
            reopen_page=reopen_page,
            cancel_sign_in=cancel_sign_in,
            callback_path="/auth/callback",
            no_browser_texts=[
                "No hay un navegador disponible para abrir la página de inicio de sesión de OpenAI.",
                "No browser is available to open the OpenAI sign-in page.",
            ],
            expected_ports=(1455, 1457),
        )
        print("== Codex sign-in (device code, the alternative)")
        use_device_code = ["Usar un código de dispositivo"] if es else ["Use a device code instead"]
        if tap_label(use_device_code, "use a device code"):
            device_code_sign_in(es, cancel_sign_in)

    print("== notification permission")
    sdk = int(adb("shell", "getprop", "ro.build.version.sdk").strip() or "0")
    notification_permission(es, settings_tab, sdk)

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
