import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app


class AnalysisRouteTest(unittest.TestCase):
    def test_analysis_job_response_includes_graph_and_counts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "models.py").write_text("class User:\n    pass\n", encoding="utf-8")
            (root / "api.py").write_text(
                """from .models import User
import jwt

router = APIRouter(prefix="/v1")

class Admin(User):
    @router.post("/users")
    def create_user(self, payload):
        return jwt.encode(payload)
""",
                encoding="utf-8",
            )

            client = TestClient(app)
            response = client.post(
                "/internal/v1/analysis-jobs",
                json={
                    "ingestionJobId": "job-123",
                    "repositoryId": "repo-123",
                    "localPath": temporary_directory,
                    "githubUrl": "https://github.com/example/project",
                },
            )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("COMPLETED", body["status"])
        self.assertGreater(body["summary"]["totalGraphNodes"], 0)
        self.assertGreater(body["summary"]["totalGraphEdges"], 0)
        self.assertEqual(body["summary"]["totalGraphNodes"], len(body["graph"]["nodes"]))
        self.assertEqual(body["summary"]["totalGraphEdges"], len(body["graph"]["edges"]))
        self.assertTrue(any(node["type"] == "repo" for node in body["graph"]["nodes"]))
        self.assertTrue(any(edge["type"] == "USES" for edge in body["graph"]["edges"]))


if __name__ == "__main__":
    unittest.main()
