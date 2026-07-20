"""Black-box assertions for the Stage 2 Phase 6 integration stack."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

API_BASE_URL = os.environ.get("API_BASE_URL", "http://api:8080").rstrip("/")
REPOSITORY_ID = "11111111-1111-1111-1111-111111111111"
SNAPSHOT_A = "22222222-2222-2222-2222-222222222222"
SNAPSHOT_B = "33333333-3333-3333-3333-333333333333"
ENTRY_NODE = "function:entry"
ROOT_CAUSE_NODE = "function:root"
ERROR_LOG = "ValueError: deterministic stage two failure"


def request_json(method: str, path: str, payload: dict | None = None) -> dict:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{API_BASE_URL}{path}",
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise AssertionError(
            f"{method} {path} returned HTTP {error.code}: {response_body}"
        ) from error


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def list_failures(snapshot_id: str) -> dict:
    return request_json(
        "GET",
        f"/api/v1/repositories/{REPOSITORY_ID}/failures?snapshotId={snapshot_id}",
    )


def localize(snapshot_id: str) -> dict:
    return request_json(
        "POST",
        "/api/v1/intelligence/failures/localize",
        {
            "repositoryId": REPOSITORY_ID,
            "snapshotId": snapshot_id,
            "failingNodeId": ENTRY_NODE,
            "errorLog": ERROR_LOG,
            "failurePathNodeIds": [ENTRY_NODE],
            "configuration": {
                "maxTraversalDepth": 1,
                "maxSuspectedRootCauses": 10,
            },
        },
    )


def candidate(result: dict, node_id: str) -> dict:
    match = next(
        (
            item
            for item in result["suspectedRootCauses"]
            if item["nodeId"] == node_id
        ),
        None,
    )
    require(match is not None, f"Expected localization candidate {node_id}")
    return match


def assert_history_influences_localization(failure_id: str) -> None:
    isolated = localize(SNAPSHOT_B)
    require(
        isolated["reasoningMetadata"]["historyRecordsCompared"] == 0,
        "Snapshot B must not receive Snapshot A history",
    )
    require(
        isolated["similarPastFailures"] == [],
        "Snapshot B must not report Snapshot A as a similar failure",
    )

    with_history = localize(SNAPSHOT_A)
    require(
        with_history["reasoningMetadata"]["historyRecordsCompared"] == 1,
        "Snapshot A must compare its persisted failure",
    )
    require(
        any(
            item["failureId"] == failure_id
            for item in with_history["similarPastFailures"]
        ),
        "Later localization must reference the persisted failure",
    )

    isolated_root = candidate(isolated, ROOT_CAUSE_NODE)
    historical_root = candidate(with_history, ROOT_CAUSE_NODE)
    reason_kinds = {reason["kind"] for reason in historical_root["reasons"]}
    require(
        "historical_failure_overlap" in reason_kinds,
        "Confirmed root cause must contribute historical evidence",
    )
    require(
        historical_root["score"] > isolated_root["score"],
        "Confirmed history must increase the root-cause score",
    )


def before_restart() -> None:
    require(list_failures(SNAPSHOT_A)["failures"] == [], "Snapshot A must start empty")
    require(list_failures(SNAPSHOT_B)["failures"] == [], "Snapshot B must start empty")

    created = request_json(
        "POST",
        f"/api/v1/repositories/{REPOSITORY_ID}/failures",
        {
            "snapshotId": SNAPSHOT_A,
            "failingNodeId": ENTRY_NODE,
            "errorLog": ERROR_LOG,
            "failurePathNodeIds": [ENTRY_NODE],
            "occurredAt": "2026-07-20T12:00:00Z",
            "localizationConfiguration": {"maxTraversalDepth": 1},
        },
    )
    failure_id = created["failureId"]
    require(created["snapshotId"] == SNAPSHOT_A, "Failure must belong to Snapshot A")
    require(created["status"] == "OPEN", "New failure must be OPEN")
    require(
        created["resolvedFailurePathNodeIds"] == [ENTRY_NODE],
        "Created failure must persist its resolved path",
    )

    updated = request_json(
        "PATCH",
        f"/api/v1/failures/{failure_id}",
        {
            "status": "RESOLVED",
            "confirmedRootCauseNodeIds": [ROOT_CAUSE_NODE],
            "resolutionNotes": "Confirmed by the real-service integration test.",
            "resolvedAt": "2026-07-20T12:05:00Z",
        },
    )
    require(updated["status"] == "RESOLVED", "Failure must be resolved")
    require(
        updated["confirmedRootCauseNodeIds"] == [ROOT_CAUSE_NODE],
        "Confirmed root cause must be persisted",
    )

    snapshot_a_failures = list_failures(SNAPSHOT_A)["failures"]
    require(len(snapshot_a_failures) == 1, "Snapshot A must contain one failure")
    require(
        list_failures(SNAPSHOT_B)["failures"] == [],
        "Snapshot B must remain isolated",
    )
    assert_history_influences_localization(failure_id)
    print(f"before-restart lifecycle passed for failure {failure_id}")


def after_restart() -> None:
    snapshot_a_failures = list_failures(SNAPSHOT_A)["failures"]
    require(len(snapshot_a_failures) == 1, "Failure must survive the API restart")
    persisted = snapshot_a_failures[0]
    require(persisted["status"] == "RESOLVED", "Resolved status must survive restart")
    require(
        persisted["confirmedRootCauseNodeIds"] == [ROOT_CAUSE_NODE],
        "Confirmed root cause must survive restart",
    )
    require(
        persisted["resolutionNotes"]
        == "Confirmed by the real-service integration test.",
        "Resolution notes must survive restart",
    )
    require(
        list_failures(SNAPSHOT_B)["failures"] == [],
        "Snapshot isolation must survive restart",
    )
    assert_history_influences_localization(persisted["failureId"])
    print(f"after-restart persistence passed for failure {persisted['failureId']}")


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in {"before-restart", "after-restart"}:
        raise SystemExit(
            "Usage: stage2_integration.py before-restart|after-restart"
        )
    if sys.argv[1] == "before-restart":
        before_restart()
    else:
        after_restart()


if __name__ == "__main__":
    main()
