import unittest

from fastapi.testclient import TestClient

from app.main import app
from tests.fixtures.phase6_graphs import failure_graph, similarity_graph


class IntelligenceRoutesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_similarity_route_returns_existing_ranking_model(self) -> None:
        response = self.client.post(
            "/internal/v1/intelligence/similarity",
            json={
                "graph": similarity_graph().model_dump(mode="json"),
                "targetNodeId": "function:login-a",
                "configuration": {"limit": 2},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("function:login-a", payload["targetNodeId"])
        self.assertEqual("function:login-b", payload["results"][0]["candidateNodeId"])
        self.assertEqual(2, len(payload["results"]))

    def test_cluster_route_returns_existing_cluster_model(self) -> None:
        response = self.client.post(
            "/internal/v1/intelligence/clusters",
            json={
                "graph": similarity_graph().model_dump(mode="json"),
                "nodeType": "function",
                "configuration": {"threshold": 0.5},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("function", payload["nodeType"])
        self.assertTrue(payload["clusters"])

    def test_localize_route_uses_repository_scoped_history(self) -> None:
        response = self.client.post(
            "/internal/v1/intelligence/localize",
            json={
                "graph": failure_graph().model_dump(mode="json"),
                "failure": {
                    "repositoryId": "repo-example",
                    "stackTrace": (
                        'File "/workspace/app/auth.py", line 24, in login\n'
                        "ValueError: Invalid token"
                    ),
                },
                "history": [
                    {
                        "failureId": "incident-login",
                        "repositoryId": "repo-example",
                        "occurredAt": "2026-07-17T10:30:00Z",
                        "failurePathNodeIds": ["function:login", "module:jwt"],
                        "errorSignature": {
                            "exceptionType": "ValueError",
                            "messageFingerprint": "invalid-token",
                        },
                        "confirmedRootCauseNodeIds": ["function:login"],
                    }
                ],
                "configuration": {"maxTraversalDepth": 1},
            },
        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("function:login", payload["suspectedRootCauses"][0]["nodeId"])
        self.assertEqual(1, payload["reasoningMetadata"]["historyRecordsCompared"])


if __name__ == "__main__":
    unittest.main()
