"""Trusted server console only. Never accepts a password through an HTTP endpoint."""
import argparse
import getpass
import os
import secrets
from pathlib import Path
from cryptography.fernet import Fernet
from .garmin import GarminAdapter, new_garmin

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["init", "login", "disconnect"])
    parser.add_argument("--directory", default="/bootstrap")
    args = parser.parse_args()
    os.umask(0o077)
    if args.command == "init":
        base = Path(args.directory)
        (base / "secrets").mkdir(parents=True, exist_ok=True, mode=0o700)
        (base / "data").mkdir(parents=True, exist_ok=True, mode=0o700)
        if (base / "data" / "garmin-session.enc").exists() and not (base / "secrets" / "vault.key").exists():
            parser.error("Existing encrypted session requires its original vault.key; restore it before initialization")
        # Exclusive creation: never rotate keys and strand existing encrypted sessions.
        for name, data in [("vault.key", Fernet.generate_key()), ("app.token", secrets.token_urlsafe(48).encode())]:
            path = base / "secrets" / name
            if not path.exists():
                fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, "wb") as output: output.write(data)
        print("Private files initialized. No credentials are printed.")
        return
    directory = Path(os.environ.get("BAIR_DATA_DIR", "/data"))
    adapter = GarminAdapter(directory, Path(os.environ.get("BAIR_VAULT_KEY_FILE", "/run/secrets/vault.key")).read_bytes())
    if args.command == "disconnect": adapter.disconnect(); print("Local Garmin session deleted."); return
    username = input("Garmin email (server console only): ").strip()
    password = getpass.getpass("Garmin password: ")
    client = new_garmin(username, password, prompt_mfa=lambda: getpass.getpass("Garmin MFA code: "))
    try:
        client.login()
        adapter.save(client)
    except Exception:
        print("Login failed. Complete genuine MFA if requested; do not bypass CAPTCHA/provider restrictions.")
        raise SystemExit(1) from None
    finally:
        password = None
        client.password = None
    print("Encrypted Garmin session saved. Password was not stored.")
if __name__ == "__main__": main()
