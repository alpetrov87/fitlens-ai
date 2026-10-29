"""Single-owner Garmin bridge. No password, session or arbitrary URL API."""
from __future__ import annotations
import hashlib
import hmac
import json
import os
import re
import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Any

MAX_BODY = 500_000
ID = re.compile(r"[1-9][0-9]{0,17}\Z")
KEY = re.compile(r"[0-9a-f]{64}\Z")

class BridgeError(Exception):
    def __init__(self, code: str, status: int = 409):
        self.code, self.status = code, status

class AuthError(Exception): pass
class RateError(Exception): pass

def remote_id(value: Any) -> str:
    result = str(value)
    if not ID.fullmatch(result):
        raise BridgeError("invalid_response", 502)
    return result

# Deliberately limited to the reviewed Android converter catalog.
EXERCISES = {("SQUAT", "AIR_SQUAT"), ("PUSH_UP", "INCLINE_PUSH_UP"),
    ("BANDED_EXERCISES", "ROW"), ("CALF_RAISE", "CALF_RAISE"),
    ("BANDED_EXERCISES", "PULL_APART"), ("PUSH_UP", "CHEST_PRESS_WITH_BAND"),
    ("HIP_STABILITY", "STANDING_HIP_ABDUCTION"), ("WARM_UP", "ARM_CIRCLES"),
    ("HIP_STABILITY", "HIP_CIRCLES")}

def validate_payload(payload: Any) -> dict:
    def integer(v: Any, low: int, high: int) -> int:
        if isinstance(v, bool) or not isinstance(v, (int, float)) or int(v) != v or not low <= v <= high:
            raise ValueError()
        return int(v)
    try:
        if not isinstance(payload, dict) or set(payload) - {"workoutName", "description", "sportType", "estimatedDurationInSecs", "workoutSegments", "workoutId"}:
            raise ValueError()
        name = payload["workoutName"]
        if not isinstance(name, str) or not name.strip() or len(name) > 120 or len(payload.get("description", "")) > 4000:
            raise ValueError()
        if payload["sportType"]["sportTypeId"] != 5 or payload["sportType"]["sportTypeKey"] != "strength_training":
            raise ValueError()
        segments = payload["workoutSegments"]
        if not isinstance(segments, list) or len(segments) != 1 or segments[0]["sportType"]["sportTypeId"] != 5:
            raise ValueError()
        orders: set[int] = set()
        count = 0
        def steps(items: Any, depth: int) -> tuple[int, bool]:
            nonlocal count
            if not isinstance(items, list) or not items or depth > 1:
                raise ValueError()
            total, exact = 0, True
            for item in items:
                count += 1
                if count > 200 or not isinstance(item, dict): raise ValueError()
                order = integer(item["stepOrder"], 1, 1000)
                if order in orders: raise ValueError()
                orders.add(order)
                kind = integer(item["stepType"]["stepTypeId"], 1, 6)
                condition = item["endCondition"]["conditionTypeId"]
                value = item["endConditionValue"]
                if item["type"] == "RepeatGroupDTO":
                    if kind != 6 or condition != 7 or depth != 0 or item.get("skipLastRestStep", False): raise ValueError()
                    repeat = integer(item["numberOfIterations"], 1, 50)
                    if value != repeat: raise ValueError()
                    seconds, timed = steps(item["workoutSteps"], depth + 1)
                    total += seconds * repeat; exact &= timed
                elif item["type"] == "ExecutableStepDTO":
                    if kind == 6 or condition not in (2, 10) or len(item.get("description", "")) > 2000: raise ValueError()
                    if item.get("targetType", {}).get("workoutTargetTypeId") != 1: raise ValueError()
                    value = integer(value, 1, 3600 if condition == 2 else 1000)
                    if condition == 2: total += value
                    else: exact = False
                    pair = (item.get("category"), item.get("exerciseName"))
                    if pair != (None, None) and pair not in EXERCISES: raise ValueError()
                    if set(item) - {"type", "stepOrder", "stepType", "endCondition", "endConditionValue", "description", "targetType", "category", "exerciseName"}: raise ValueError()
                else: raise ValueError()
            if total > 86400: raise ValueError()
            return total, exact
        total, exact = steps(segments[0]["workoutSteps"], 0)
        estimate = integer(payload["estimatedDurationInSecs"], 0, 86400)
        if estimate != (total if exact else 0): raise ValueError()
        return json.loads(json.dumps(payload, allow_nan=False))
    except (ValueError, TypeError, KeyError, OverflowError):
        raise BridgeError("validation", 422) from None

class Service:
    def __init__(self, directory: Path, adapter: Any):
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.db = directory / "requests.sqlite3"
        self.adapter = adapter
        self.lock = threading.RLock()
        with self.connection() as db:
            db.execute("CREATE TABLE IF NOT EXISTS requests (owner TEXT NOT NULL, key TEXT NOT NULL, digest TEXT NOT NULL, state TEXT NOT NULL, remote_id TEXT, PRIMARY KEY(owner,key))")
        os.chmod(self.db, 0o600)
    @contextmanager
    def connection(self):
        db = sqlite3.connect(self.db, timeout=20)
        try:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("PRAGMA synchronous=FULL")
            with db: yield db
        finally: db.close()
    def owner(self):
        if not self.adapter.connected(): raise BridgeError("expired", 401)
        owner = self.adapter.owner()
        if not isinstance(owner, str) or not KEY.fullmatch(owner): raise BridgeError("expired", 401)
        return owner
    def status(self) -> dict:
        with self.lock:
            try: return {"connected": True, "owner": self.owner()}
            except Exception: return {"connected": False}
    def disconnect(self):
        with self.lock: self.adapter.disconnect()
    def lookup(self, key: str, expected_owner: str | None = None) -> dict:
        if not KEY.fullmatch(key): raise BridgeError("validation", 422)
        with self.lock, self.connection() as db:
            owner = self.owner()
            if expected_owner is not None and owner != expected_owner: raise BridgeError("account_mismatch", 412)
            row = db.execute("SELECT state, remote_id FROM requests WHERE owner=? AND key=?", (owner, key)).fetchone()
        if row and row[0] == "done": return {"workoutId": row[1]}
        raise BridgeError("uncertain", 409)
    def send(self, key: str, payload: dict, update_id: str | None = None, expected_owner: str | None = None) -> dict:
        if not KEY.fullmatch(key): raise BridgeError("validation", 422)
        payload = validate_payload(payload)
        if update_id is None: payload.pop("workoutId", None)
        else: payload["workoutId"] = int(remote_id(update_id))
        digest = hashlib.sha256(json.dumps([update_id, payload], sort_keys=True, separators=(",", ":"), allow_nan=False).encode()).hexdigest()
        with self.lock:
            owner = self.owner()
            if expected_owner is not None and owner != expected_owner: raise BridgeError("account_mismatch", 412)
            with self.connection() as db:
                db.execute("BEGIN IMMEDIATE")
                row = db.execute("SELECT digest,state,remote_id FROM requests WHERE owner=? AND key=?", (owner, key)).fetchone()
                if row:
                    if not hmac.compare_digest(digest, row[0]): raise BridgeError("request_conflict", 409)
                    if row[1] == "done": return {"workoutId": row[2]}
                    raise BridgeError("uncertain", 409)
                # Durable intent before any request that might create a workout.
                db.execute("INSERT INTO requests VALUES (?,?,?,'pending',NULL)", (owner, key, digest))
            try:
                answer = self.adapter.upload(payload, update_id)
                result = remote_id(answer.get("workoutId"))
                if update_id is not None and result != update_id: raise BridgeError("invalid_response", 502)
            except (AuthError, RateError) as error:
                # Only confirmed authentication/rate rejections are safe to retry.
                with self.connection() as db: db.execute("DELETE FROM requests WHERE owner=? AND key=?", (owner, key))
                raise BridgeError("expired" if isinstance(error, AuthError) else "rate_limit", 401 if isinstance(error, AuthError) else 429) from None
            except Exception:
                # May already exist at Garmin. Never blindly submit this key again.
                raise BridgeError("uncertain", 409) from None
            with self.connection() as db: db.execute("UPDATE requests SET state='done',remote_id=? WHERE owner=? AND key=?", (result, owner, key))
            return {"workoutId": result}
