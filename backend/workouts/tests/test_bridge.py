import copy
import hashlib
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from cryptography.fernet import Fernet
from fastapi.testclient import TestClient
from backend.workouts.app import create_app
from backend.workouts.service import Service, BridgeError, AuthError, RateError, validate_payload
from backend.workouts.garmin import GarminAdapter, new_garmin

TOKEN = "synthetic-test-key-not-a-real-secret-" + "x" * 32
REQUEST = hashlib.sha256(b"synthetic workout").hexdigest()

def workout():
    sport = {"sportTypeId": 5, "sportTypeKey": "strength_training"}
    def step(order, seconds, kind=3):
        return {"type": "ExecutableStepDTO", "stepOrder": order, "stepType": {"stepTypeId": kind}, "endCondition": {"conditionTypeId": 2},
                "endConditionValue": seconds, "targetType": {"workoutTargetTypeId": 1}, "description": "Synthetic exercise"}
    steps = [step(i + 1, 30, 1) for i in range(4)]
    steps.append({"type": "RepeatGroupDTO", "stepOrder": 5, "stepType": {"stepTypeId": 6}, "endCondition": {"conditionTypeId": 7},
        "endConditionValue": 2, "numberOfIterations": 2, "skipLastRestStep": False,
        "workoutSteps": [step(i + 6, 40 if i % 2 == 0 else 20, 3 if i % 2 == 0 else 5) for i in range(12)]})
    return {"workoutName": "Synthetic strength", "description": "Tests only", "sportType": sport, "estimatedDurationInSecs": 840,
            "workoutSegments": [{"sportType": sport, "segmentOrder": 1, "workoutSteps": steps}]}

class Fake:
    def __init__(self): self.live = True; self.calls = 0; self.error = None; self.identity = "a" * 64; self.answer = {"workoutId": 123}
    def connected(self): return self.live
    def owner(self): return self.identity
    def disconnect(self): self.live = False
    def upload(self, payload, update=None):
        self.calls += 1
        if self.error: raise self.error
        return self.answer

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.fake = Fake(); self.service = Service(Path(self.temp.name), self.fake)
        self.client = TestClient(create_app(self.service, TOKEN))
        self.headers = {"Authorization": "Bearer " + TOKEN, "Idempotency-Key": REQUEST, "X-Bair-Owner": self.fake.identity}
    def test_realistic_template_and_update_return_actual_id(self):
        r = self.client.post("/v1/workouts", headers=self.headers, json=workout())
        self.assertEqual(r.status_code, 200); self.assertEqual(r.json(), {"workoutId": "123"})
        h = dict(self.headers, **{"Idempotency-Key": hashlib.sha256(b"update").hexdigest()})
        self.assertEqual(self.client.put("/v1/workouts/123", headers=h, json=workout()).json(), {"workoutId": "123"})
    def test_persistent_idempotency_and_lookup(self):
        self.service.send(REQUEST, workout())
        restarted = Service(Path(self.temp.name), self.fake)
        self.assertEqual(restarted.send(REQUEST, workout()), {"workoutId": "123"})
        self.assertEqual(self.fake.calls, 1)
        self.assertEqual(self.client.get("/v1/requests/" + REQUEST, headers=self.headers).json(), {"workoutId": "123"})
    def test_reused_key_different_body_is_rejected(self):
        self.service.send(REQUEST, workout()); edited = workout(); edited["workoutName"] = "Changed"
        with self.assertRaises(BridgeError): self.service.send(REQUEST, edited)
        self.assertEqual(self.fake.calls, 1)
    def test_unknown_outcome_and_invalid_response_cannot_duplicate(self):
        for error in [TimeoutError("must not appear in response"), None]:
            with tempfile.TemporaryDirectory() as temp:
                fake = Fake(); fake.error = error; fake.answer = {}
                service = Service(Path(temp), fake)
                for _ in range(2):
                    with self.assertRaises(BridgeError): service.send(REQUEST, workout())
                self.assertEqual(fake.calls, 1)
    def test_confirmed_auth_and_rate_rejections_allow_safe_retry(self):
        for error in [AuthError(), RateError()]:
            self.fake.error = error
            with self.assertRaises(BridgeError): self.service.send(REQUEST, workout())
            self.fake.error = None
            self.assertEqual(self.service.send(REQUEST, workout()), {"workoutId": "123"})
            with self.service.connection() as db: db.execute("DELETE FROM requests")
    def test_credentials_never_accepted_or_returned_by_api(self):
        for method, path in [("get", "/v1/session"), ("delete", "/v1/session"), ("post", "/v1/workouts"), ("get", "/v1/requests/" + REQUEST)]:
            self.assertEqual(getattr(self.client, method)(path).status_code, 403)
        self.assertEqual(self.client.post("/login", json={"password": "synthetic"}).status_code, 404)
        self.assertEqual(self.fake.calls, 0)
    def test_disconnect_and_expiry(self):
        self.assertTrue(self.client.get("/v1/session", headers=self.headers).json()["connected"])
        self.assertFalse(self.client.delete("/v1/session", headers=self.headers).json()["connected"])
        self.assertEqual(self.client.post("/v1/workouts", headers=self.headers, json=workout()).status_code, 401)
        self.assertEqual(self.fake.calls, 0)
    def test_malformed_large_and_unsupported_payloads(self):
        self.assertEqual(self.client.post("/v1/workouts", headers=self.headers, content="x" * 500001).status_code, 413)
        self.assertEqual(self.client.post("/v1/workouts", headers=self.headers, content="{broken").status_code, 422)
        for change in [lambda p: p.update(estimatedDurationInSecs=839), lambda p: p["sportType"].update(sportTypeId=1),
                       lambda p: p["workoutSegments"][0]["workoutSteps"][0].update(endConditionValue=-1)]:
            payload = workout(); change(payload)
            self.assertEqual(self.client.post("/v1/workouts", headers=self.headers, json=payload).status_code, 422)
        self.assertEqual(self.fake.calls, 0)
    def test_owner_change_between_status_and_upload_is_rejected_before_submission(self):
        headers = dict(self.headers, **{"X-Bair-Owner": "b" * 64})
        self.assertEqual(self.client.post("/v1/workouts", headers=headers, json=workout()).status_code, 412)
        self.assertEqual(self.fake.calls, 0)
    def test_account_switch_never_replays_other_owners_id(self):
        self.service.send(REQUEST, workout()); self.fake.identity = "b" * 64; self.fake.answer = {"workoutId": 456}
        with self.assertRaises(BridgeError): self.service.lookup(REQUEST)
        self.assertEqual(self.service.send(REQUEST, workout()), {"workoutId": "456"})
        self.assertEqual(self.fake.calls, 2)
    def test_rate_limit(self):
        for _ in range(60): self.client.get("/v1/session", headers=self.headers)
        self.assertEqual(self.client.get("/v1/session", headers=self.headers).status_code, 429)
    def test_upstream_automatic_retries_and_login_fallbacks_are_disabled(self):
        import sys
        from unittest.mock import patch
        captured = {}
        def construct(*args, **kwargs):
            captured.update(kwargs)
            return SimpleNamespace(client=SimpleNamespace(skip_strategies=set()))
        with patch.dict(sys.modules, {"garminconnect": SimpleNamespace(Garmin=construct)}):
            client = new_garmin()
        self.assertEqual(captured["retry_attempts"], 0)
        self.assertIn("widget+cffi", client.client.skip_strategies)
        self.assertNotIn("mobile+requests", client.client.skip_strategies)
    def test_session_encryption_and_delete(self):
        adapter = GarminAdapter(Path(self.temp.name), Fernet.generate_key())
        client = SimpleNamespace(profile_id=123, client=SimpleNamespace(dumps=lambda: '{"token":"synthetic-only"}'))
        adapter.save(client)
        self.assertNotIn(b"synthetic-only", adapter.session.read_bytes())
        self.assertEqual(adapter.session.stat().st_mode & 0o777, 0o600)
        self.assertEqual(len(adapter.owner()), 64)
        adapter.disconnect(); self.assertFalse(adapter.session.exists())

if __name__ == "__main__": unittest.main()
