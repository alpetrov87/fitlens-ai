"""Garmin sessions stay encrypted at rest; interactive login is CLI-only."""
import logging
import hashlib
import hmac
import json
import os
from pathlib import Path
from cryptography.fernet import Fernet
from .service import AuthError, RateError

logging.getLogger("garminconnect").setLevel(logging.CRITICAL)

def new_garmin(email=None, password=None, prompt_mfa=None):
    from garminconnect import Garmin
    # Never retry a possibly accepted POST inside the upstream library.
    client = Garmin(email, password, prompt_mfa=prompt_mfa, retry_attempts=0)
    # One ordinary SSO flow only: no browser-impersonation or rate-limit fallback.
    client.client.skip_strategies = {"mobile+cffi", "widget+cffi", "portal+cffi", "portal+requests"}
    return client

class GarminAdapter:
    def __init__(self, directory: Path, key: bytes):
        self.directory = directory
        self.session = directory / "garmin-session.enc"
        self.vault = Fernet(key.strip())
        self.identity_key = hashlib.sha256(b"bair-account-binding:" + key.strip()).digest()
    def binding(self, profile):
        return hmac.new(self.identity_key, str(profile).encode(), hashlib.sha256).hexdigest()
    def save(self, client):
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        target = self.session.with_suffix(".tmp")
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "wb") as output:
            profile = client.profile_id
            if not isinstance(profile, int) or profile <= 0: raise AuthError()
            owner = self.binding(profile)
            output.write(self.vault.encrypt(json.dumps({"owner": owner, "tokens": client.client.dumps()}).encode()))
            output.flush(); os.fsync(output.fileno())
        os.replace(target, self.session)
    def client(self):
        if not self.session.exists(): raise AuthError()
        try:
            saved = json.loads(self.vault.decrypt(self.session.read_bytes()))
            client = new_garmin()
            client.login(tokenstore=saved["tokens"])
            if self.binding(client.profile_id) != saved["owner"]: raise AuthError()
            return client
        except Exception: raise AuthError() from None
    def owner(self):
        return json.loads(self.vault.decrypt(self.session.read_bytes()))["owner"]
    def connected(self):
        try:
            client = self.client(); self.save(client); return True
        except AuthError: return False
    def upload(self, payload, workout_id=None):
        from garminconnect import GarminConnectAuthenticationError, GarminConnectTooManyRequestsError
        client = self.client()
        try:
            result = client.update_workout(workout_id, payload) if workout_id else client.upload_workout(payload)
            self.save(client)
            return result
        except GarminConnectAuthenticationError: raise AuthError() from None
        except GarminConnectTooManyRequestsError: raise RateError() from None
        # All other failures remain uncertain; no raw provider body escapes.
    def disconnect(self):
        self.session.unlink(missing_ok=True)
        self.session.with_suffix(".tmp").unlink(missing_ok=True)
        # Removes local sessions, not a claim of Garmin-side token revocation.
