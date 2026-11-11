#!/usr/bin/env python3
"""Remote management server for the custom A21s ROM.

    gan_server.py init --url http://my-server:8750    create keys and passwords
    gan_server.py run  [--port 8750]                  start the server

The device asks for its policy at /api/policy and posts its status to
/api/report. Every policy is signed with a private key that never leaves
this machine; the device refuses anything not signed with it.
The control panel is served at / and is protected by a password.
"""
import argparse
import base64
import hashlib
import hmac
import html
import json
import os
import re
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

DATA_DIR = Path(os.environ.get("GAN_DATA_DIR", Path(__file__).resolve().parent / "data"))
KEY_FILE = DATA_DIR / "policy_key.pem"
CONFIG_FILE = DATA_DIR / "server_config.json"
POLICY_FILE = DATA_DIR / "policy.json"
REPORT_FILE = DATA_DIR / "last_report.json"
DEVICE_CONFIG_FILE = DATA_DIR / "device_config.json"

PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*(\.\*|\*)?$")
HOURS_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d-([01]\d|2[0-3]):[0-5]\d$")
MAX_BODY = 1024 * 1024

lock = threading.Lock()


def read_json(path, default=None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return default


def write_json(path, value, private=False):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    if private:
        tmp.chmod(0o600)
    tmp.replace(path)


def hash_password(password, salt):
    return hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1).hex()


def load_key():
    return serialization.load_pem_private_key(KEY_FILE.read_bytes(), password=None)


def signed_policy():
    """The current policy as the exact bytes that were signed, plus the signature."""
    payload = json.dumps(read_json(POLICY_FILE), ensure_ascii=False, sort_keys=True).encode()
    signature = load_key().sign(payload, ec.ECDSA(hashes.SHA256()))
    return {
        "payload": base64.b64encode(payload).decode(),
        "signature": base64.b64encode(signature).decode(),
    }


def cmd_init(args):
    if KEY_FILE.exists():
        sys.exit(f"Already initialised: {KEY_FILE} exists. Delete {DATA_DIR} to start over.")
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    DATA_DIR.chmod(0o700)

    key = ec.generate_private_key(ec.SECP256R1())
    KEY_FILE.write_bytes(key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption()))
    KEY_FILE.chmod(0o600)
    public_key = base64.b64encode(key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()

    admin_password = secrets.token_urlsafe(12)
    device_token = secrets.token_urlsafe(32)
    salt = secrets.token_bytes(16)
    write_json(CONFIG_FILE, {
        "admin_salt": salt.hex(),
        "admin_hash": hash_password(admin_password, salt),
        "device_token": device_token,
        "csrf_secret": secrets.token_hex(32),
    }, private=True)
    write_json(POLICY_FILE, {
        "version": 1, "allowed_packages": [], "locked": False, "lock_hours": []})
    write_json(DEVICE_CONFIG_FILE, {
        "server_url": args.url.rstrip("/"),
        "device_token": device_token,
        "policy_public_key": public_key,
    })

    print("Server initialised.")
    print(f"  Control panel user:     admin")
    print(f"  Control panel password: {admin_password}   (shown only now, write it down)")
    print(f"  Copy this file into the ROM before building:")
    print(f"    {DEVICE_CONFIG_FILE}  ->  rom/vendor_extra/gan/device_config.json")


PAGE = """<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ניהול המכשיר</title>
<style>
body{{font-family:system-ui,sans-serif;max-width:720px;margin:0 auto;padding:16px;background:#f4f5f7;color:#1c1e21}}
section{{background:#fff;border-radius:10px;padding:16px;margin-bottom:16px;box-shadow:0 1px 3px #0002}}
h1{{font-size:1.4rem}} h2{{font-size:1.1rem;margin-top:0}}
textarea{{width:100%;box-sizing:border-box;font-family:monospace;direction:ltr;text-align:left;padding:8px}}
button{{font-size:1rem;padding:10px 20px;border:0;border-radius:8px;background:#1a56db;color:#fff;cursor:pointer}}
table{{border-collapse:collapse;width:100%}} td{{padding:4px 0;vertical-align:top}}
td:first-child{{color:#555;width:40%}} .ltr{{direction:ltr;text-align:left;font-family:monospace}}
.note{{color:#555;font-size:.9rem}} .ok{{background:#def7ec;padding:10px;border-radius:8px}}
label.big{{font-size:1.05rem}}
</style></head><body>
<h1>ניהול המכשיר</h1>
{saved}
<section><h2>מצב המכשיר</h2>{status}</section>
<form method="post" action="/save">
<input type="hidden" name="csrf" value="{csrf}">
<section><h2>נעילה</h2>
<p><label class="big"><input type="checkbox" name="locked" {locked}> לנעול את המכשיר עכשיו</label></p>
<p>שעות נעילה קבועות, טווח אחד בכל שורה, למשל <span class="ltr">22:00-06:00</span></p>
<textarea name="lock_hours" rows="3">{lock_hours}</textarea>
<p class="note">בזמן נעילה המסך נכבה מיד בכל פעם שמדליקים אותו. גם שיחות חירום לא זמינות בזמן הזה.</p>
</section>
<section><h2>אפליקציות שמותר להתקין</h2>
<p>שם חבילה אחד בכל שורה. כוכבית בסוף מתירה את כל החבילות שמתחילות כך.</p>
<textarea name="allowed_packages" rows="10">{allowed}</textarea>
</section>
<button type="submit">שמירה ושליחה למכשיר</button>
<p class="note">גרסת הוראות נוכחית: {version}. המכשיר מושך הוראות חדשות כל רבע שעה בערך, ובכל הדלקת מסך.</p>
</form></body></html>"""


def render_status():
    report = read_json(REPORT_FILE)
    if not report:
        return "<p>המכשיר עוד לא התחבר לשרת.</p>"
    seen = time.strftime("%d/%m/%Y %H:%M", time.localtime(report.get("received_at", 0)))
    rows = [
        ("התחבר לאחרונה", seen),
        ("סוללה", f"{report.get('battery', '?')}%"),
        ("גרסת הוראות במכשיר", report.get("policy_version", "?")),
        ("גרסת מערכת", report.get("build", "?")),
    ]
    out = "<table>" + "".join(
        f"<tr><td>{html.escape(str(k))}</td><td>{html.escape(str(v))}</td></tr>" for k, v in rows)
    packages = "<br>".join(html.escape(str(p)) for p in report.get("packages", [])) or "אין"
    out += f"<tr><td>אפליקציות שהותקנו</td><td class='ltr'>{packages}</td></tr></table>"
    return out


class Handler(BaseHTTPRequestHandler):
    server_version = "GanServer"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))

    def send(self, code, body, content_type="application/json", headers=()):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        for name, value in headers:
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(data)

    def read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length < 0 or length > MAX_BODY:
            return None
        return self.rfile.read(length)

    def is_device(self):
        token = self.headers.get("X-Device-Token", "")
        return hmac.compare_digest(token.encode(), self.server.config["device_token"].encode())

    def is_admin(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            return False
        try:
            user, _, password = base64.b64decode(header[6:]).decode().partition(":")
        except ValueError:
            return False
        config = self.server.config
        digest = hash_password(password, bytes.fromhex(config["admin_salt"]))
        return user == "admin" and hmac.compare_digest(digest, config["admin_hash"])

    def require_admin(self):
        if self.is_admin():
            return True
        self.send(401, "Login required", "text/plain",
                  [("WWW-Authenticate", 'Basic realm="gan", charset="UTF-8"')])
        return False

    def csrf_token(self):
        return hmac.new(self.server.config["csrf_secret"].encode(), b"save", "sha256").hexdigest()

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/policy":
            if not self.is_device():
                return self.send(403, '{"error":"forbidden"}')
            with lock:
                return self.send(200, json.dumps(signed_policy()))
        if path == "/":
            if not self.require_admin():
                return
            with lock:
                policy = read_json(POLICY_FILE)
                page = PAGE.format(
                    saved='<p class="ok">נשמר.</p>' if "saved=1" in self.path else "",
                    status=render_status(),
                    csrf=self.csrf_token(),
                    locked="checked" if policy["locked"] else "",
                    lock_hours=html.escape("\n".join(policy["lock_hours"])),
                    allowed=html.escape("\n".join(policy["allowed_packages"])),
                    version=policy["version"])
            return self.send(200, page, "text/html", [("Cache-Control", "no-store")])
        self.send(404, "Not found", "text/plain")

    def do_POST(self):
        path = self.path.split("?")[0]
        body = self.read_body()
        if body is None:
            return self.send(413, "Too large", "text/plain")
        if path == "/api/report":
            if not self.is_device():
                return self.send(403, '{"error":"forbidden"}')
            try:
                report = json.loads(body)
                assert isinstance(report, dict)
            except (ValueError, AssertionError):
                return self.send(400, '{"error":"bad json"}')
            report["received_at"] = int(time.time())
            with lock:
                write_json(REPORT_FILE, report)
            return self.send(200, '{"ok":true}')
        if path == "/save":
            if not self.require_admin():
                return
            form = parse_qs(body.decode("utf-8", "replace"))
            if not hmac.compare_digest(form.get("csrf", [""])[0], self.csrf_token()):
                return self.send(403, "Bad form token", "text/plain")
            packages = [l.strip() for l in form.get("allowed_packages", [""])[0].splitlines() if l.strip()]
            hours = [l.strip().replace(" ", "") for l in form.get("lock_hours", [""])[0].splitlines() if l.strip()]
            bad = [p for p in packages if not PACKAGE_RE.match(p)] + [h for h in hours if not HOURS_RE.match(h)]
            if bad:
                return self.send(400, "שורות לא תקינות: " + ", ".join(bad), "text/plain")
            with lock:
                policy = read_json(POLICY_FILE)
                policy.update(
                    version=policy["version"] + 1,
                    allowed_packages=sorted(set(packages)),
                    lock_hours=hours,
                    locked="locked" in form)
                write_json(POLICY_FILE, policy)
            return self.send(303, "", "text/plain", [("Location", "/?saved=1")])
        self.send(404, "Not found", "text/plain")


def cmd_run(args):
    config = read_json(CONFIG_FILE)
    if not config:
        sys.exit("Not initialised. Run: gan_server.py init --url http://<server-address>:8750")
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.config = config
    print(f"Listening on {args.host}:{args.port}")
    server.serve_forever()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p_init = sub.add_parser("init", help="create keys and passwords")
    p_init.add_argument("--url", required=True, help="address the phone will use to reach this server")
    p_init.set_defaults(func=cmd_init)
    p_run = sub.add_parser("run", help="start the server")
    p_run.add_argument("--host", default="0.0.0.0")
    p_run.add_argument("--port", type=int, default=8750)
    p_run.set_defaults(func=cmd_run)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
