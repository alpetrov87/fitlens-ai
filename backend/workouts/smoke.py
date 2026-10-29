"""Container packaging check, with disposable generated keys and NO Garmin login."""
import os
import secrets
import tempfile
from pathlib import Path
from cryptography.fernet import Fernet
from .app import production_app
from .garmin import new_garmin

def main():
    with tempfile.TemporaryDirectory() as temp:
        base = Path(temp)
        (base / "vault.key").write_bytes(Fernet.generate_key())
        (base / "app.token").write_text(secrets.token_urlsafe(48))
        os.environ.update(BAIR_DATA_DIR=str(base / "data"), BAIR_VAULT_KEY_FILE=str(base / "vault.key"), BAIR_APP_TOKEN_FILE=str(base / "app.token"))
        app = production_app()
        assert app.docs_url is None
        client = new_garmin()
        assert "widget+cffi" in client.client.skip_strategies
        assert "mobile+requests" not in client.client.skip_strategies
        print("Rootless/read-only backend packaging and pinned Garmin client: OK. No login attempted.")
if __name__ == "__main__": main()
