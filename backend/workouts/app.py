"""Run behind a TLS reverse proxy; bind published Docker port to loopback only."""
import hashlib
import hmac
import json
import os
import threading
import time
from pathlib import Path
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool
from .service import BridgeError, MAX_BODY, Service, KEY
from .garmin import GarminAdapter

def create_app(service: Service, app_token: str) -> FastAPI:
    if len(app_token) < 40: raise ValueError("Pairing token too short")
    token_hash = hashlib.sha256(app_token.encode()).digest()
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
    lock = threading.Lock()
    attempts = []
    def authenticate(request: Request):
        header = request.headers.get("authorization", "")
        supplied = header[7:] if header.startswith("Bearer ") else ""
        if len(supplied) > 512 or not hmac.compare_digest(hashlib.sha256(supplied.encode()).digest(), token_hash):
            raise HTTPException(403, detail="authentication")
        with lock:
            now = time.monotonic()
            attempts[:] = [t for t in attempts if t > now - 60]
            if len(attempts) >= 60: raise HTTPException(429, detail="rate_limit")
            attempts.append(now)
    @app.exception_handler(BridgeError)
    async def bridge_error(_request, error):
        return JSONResponse({"error": error.code}, status_code=error.status)
    @app.exception_handler(Exception)
    async def unexpected(_request, _error):
        return JSONResponse({"error": "uncertain"}, status_code=500)
    @app.get("/health")
    def health(): return {"status": "ok"}
    @app.get("/v1/session", dependencies=[Depends(authenticate)])
    def status(): return service.status()
    @app.delete("/v1/session", dependencies=[Depends(authenticate)])
    def disconnect(): service.disconnect(); return {"connected": False}
    def owner(request: Request):
        value = request.headers.get("x-bair-owner", "")
        if not KEY.fullmatch(value): raise HTTPException(422, detail="validation")
        return value
    @app.get("/v1/requests/{key}", dependencies=[Depends(authenticate)])
    def lookup(key: str, request: Request): return service.lookup(key, owner(request))
    async def payload(request: Request):
        chunks = bytearray()
        async for chunk in request.stream():
            chunks.extend(chunk)
            if len(chunks) > MAX_BODY: raise HTTPException(413, detail="validation")
        try:
            return json.loads(chunks, parse_constant=lambda _value: (_ for _ in ()).throw(ValueError()))
        except (ValueError, RecursionError): raise HTTPException(422, detail="validation") from None
    @app.post("/v1/workouts", dependencies=[Depends(authenticate)])
    async def create(request: Request):
        return await run_in_threadpool(service.send, request.headers.get("idempotency-key", ""), await payload(request), None, owner(request))
    @app.put("/v1/workouts/{workout_id}", dependencies=[Depends(authenticate)])
    async def update(workout_id: str, request: Request):
        return await run_in_threadpool(service.send, request.headers.get("idempotency-key", ""), await payload(request), workout_id, owner(request))
    return app

def production_app():
    directory = Path(os.environ.get("BAIR_DATA_DIR", "/data"))
    key = Path(os.environ.get("BAIR_VAULT_KEY_FILE", "/run/secrets/vault.key")).read_bytes()
    token = Path(os.environ.get("BAIR_APP_TOKEN_FILE", "/run/secrets/app.token")).read_text().strip()
    return create_app(Service(directory, GarminAdapter(directory, key)), token)
