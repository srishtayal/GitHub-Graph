import unittest

from app.schemas.failure_analysis import FailureInput
from app.services.failure_path_parser import FailurePathParser
from tests.fixtures.phase6_graphs import failure_graph


class FailurePathParserTest(unittest.TestCase):
    def test_resolves_node_ids_stack_frames_and_error_signature(self) -> None:
        result = FailurePathParser().parse(
            failure_graph(),
            FailureInput(
                repositoryId="repo-example",
                failingNodeId="function:login",
                stackTrace='Traceback\n  File "/workspace/app/auth.py", line 24, in login\nValueError: Invalid token',
            ),
        )

        self.assertEqual(["function:login"], result.nodeIds)
        self.assertEqual(["function:login"], result.stackFrameNodeIds)
        self.assertEqual("ValueError", result.errorSignature.exceptionType)
        self.assertEqual("invalid-token", result.errorSignature.messageFingerprint)

    def test_reports_unknown_ids_and_external_stack_frames_without_creating_nodes(self) -> None:
        result = FailurePathParser().parse(
            failure_graph(),
            FailureInput(
                repositoryId="repo-example",
                failurePathNodeIds=["function:missing"],
                stackTrace='File "/site-packages/jwt/api.py", line 11, in decode',
            ),
        )

        self.assertEqual([], result.nodeIds)
        self.assertEqual(["node_id", "stack_frame"], [item.kind for item in result.unresolvedReferences])


if __name__ == "__main__":
    unittest.main()
