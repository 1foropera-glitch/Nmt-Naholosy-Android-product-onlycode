"""Read-only release inventory. Never prints passwords, API keys or private key contents."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_KEYS = {
    "ADMOB_APP_ID", "ADMOB_INTERSTITIAL_ID", "PRIVACY_POLICY_URL",
    "ACCOUNT_DELETION_URL", "SUPPORT_EMAIL",
}


def properties(path):
    values = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            match = re.match(r"^\s*([A-Z_]+)\s*[=:]\s*(.*?)\s*$", line)
            if match and match[1] in PUBLIC_KEYS:
                values[match[1]] = match[2].replace(r"\:", ":").replace(r"\=", "=")
    return values


def firebase(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        clients = data.get("client", [])
        matching = [c for c in clients if c.get("client_info", {}).get("android_client_info", {})
                    .get("package_name") == "com.intervalninaholosy.nmt"]
        return {
            "project_id": data.get("project_info", {}).get("project_id"),
            "package_matches": bool(matching),
            "web_oauth_present": any(o.get("client_type") == 3 for c in matching for o in c.get("oauth_client", [])),
        }
    except (OSError, ValueError, TypeError, AttributeError):
        return None


def public_https(value):
    try:
        url = urlsplit(value)
        host = (url.hostname or "").lower()
        return url.scheme == "https" and bool(host) and url.username is None and url.password is None \
            and host not in {"localhost", "example.com", "example.org"} and not host.endswith(".example")
    except ValueError:
        return False


def inventory():
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    values = {"SUPPORT_EMAIL": "wrocki.devoloper@gmail.com"}
    for key in PUBLIC_KEYS:
        if f"ORG_GRADLE_PROJECT_{key}" in os.environ:
            values[key] = os.environ[f"ORG_GRADLE_PROJECT_{key}"]
    values.update(properties(ROOT / "gradle.properties"))
    values.update(properties(gradle_home / "gradle.properties"))
    dev = firebase(ROOT / "app/src/debug/google-services.json")
    if dev is None:
        dev = firebase(ROOT / "app/google-services.json")
    prod = firebase(ROOT / "app/src/release/google-services.json")
    prod_id = (prod or {}).get("project_id")
    dev_id = (dev or {}).get("project_id")
    checks = []

    def add(name, ok, action):
        checks.append({"check": name, "status": "present" if ok else "missing_or_invalid", "next_action": "" if ok else action})

    add("production_firebase", bool(prod_id) and not prod_id.startswith("demo-")
        and bool((prod or {}).get("package_matches")), "Save production Android config to app/src/release/google-services.json.")
    add("separate_firebase_projects", bool(prod_id) and bool(dev_id) and prod_id != dev_id,
        "Use distinct development and production Firebase project IDs.")
    add("production_web_oauth", bool((prod or {}).get("web_oauth_present")),
        "Enable Google Sign-In, register signing fingerprints, download fresh production google-services.json.")
    for key, separator in [("ADMOB_APP_ID", "~"), ("ADMOB_INTERSTITIAL_ID", "/")]:
        value = values.get(key, "")
        add(key, re.fullmatch(r"ca-app-pub-[0-9]+" + re.escape(separator) + r"[0-9]+", value) is not None
            and "3940256099942544" not in value, f"Set the production {key} Gradle property locally.")
    for key in ["PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL"]:
        add(key, public_https(values.get(key, "")), f"Publish the approved page, then set {key}.")
    add("SUPPORT_EMAIL", re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", values.get("SUPPORT_EMAIL", "")) is not None,
        "Set the public support email Gradle property.")
    try:
        play_key = "".join((ROOT / "play-licensing-public-key.txt").read_text(encoding="utf-8").split())
        play_key_present = len(base64.b64decode(play_key, validate=True)) >= 256
    except (OSError, ValueError):
        play_key_present = False
    add("play_licensing_public_key", play_key_present,
        "Save the public Google Play licensing key as Base64 in play-licensing-public-key.txt; the release build validates RSA.")
    key_path = os.environ.get("KEYSTORE_PATH", "")
    key_file = Path(key_path) if key_path else None
    if key_file is not None and not key_file.is_absolute():
        key_file = ROOT / "app" / key_file
    add("upload_key_file", key_file is not None and key_file.is_file(), "Set KEYSTORE_PATH to the existing upload key; do not send its contents.")
    for name in ["STORE_PASSWORD", "KEY_PASSWORD"]:
        add(name, bool(os.environ.get(name)), f"Provide {name} only in the local build environment.")
    return {
        "development_project_id": dev_id,
        "production_project_id": prod_id,
        "checks": checks,
        "scope": "Local presence/format only. Does not verify passwords, cloud access, public pages, deletion, consent or store acceptance. Final authority for build inputs: :app:verifyReleaseConfiguration.",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--strict", action="store_true", help="Exit nonzero if local configuration is incomplete.")
    args = parser.parse_args()
    report = inventory()
    if args.json:
        print(json.dumps(report, ensure_ascii=True, indent=2))
    else:
        print("Development project:", report["development_project_id"] or "not configured")
        print("Production project:", report["production_project_id"] or "not configured")
        for check in report["checks"]:
            print(f"{check['status'].upper()}: {check['check']}")
            if check["next_action"]:
                print("  " + check["next_action"])
        print(report["scope"])
    if args.strict and any(c["status"] != "present" for c in report["checks"]):
        raise SystemExit(1)
