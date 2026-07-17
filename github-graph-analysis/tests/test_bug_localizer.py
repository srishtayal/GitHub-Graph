import unittest

from app.schemas.failure_analysis import FailureInput, HistoricalFailure, LocalizationConfiguration
from app.services.bug_localizer import BugLocalizer
from tests.fixtures.phase6_graphs import failure_graph


class BugLocalizerTest(unittest.TestCase):
    def test_ranks_resolved_failure_node_using_stack_and_history_evidence(self) -> None:
        history = [
            HistoricalFailure(
                failureId="incident-login",
                repositoryId="repo-example",
                occurredAt="2026-07-17T10:30:00Z",
                failurePathNodeIds=["function:login", "module:jwt"],
                errorSignature={"exceptionType": "ValueError", "messageFingerprint": "invalid-token"},
                confirmedRootCauseNodeIds=["function:login"],
            )
        ]
        result = BugLocalizer().localize(
            failure_graph(),
            FailureInput(
                repositoryId="repo-example",
                stackTrace='File "/workspace/app/auth.py", line 24, in login\nValueError: Invalid token',
            ),
            history,
            LocalizationConfiguration(maxTraversalDepth=1),
        )

        self.assertEqual("function:login", result.suspectedRootCauses[0].nodeId)
        self.assertEqual("high", result.suspectedRootCauses[0].confidence)
        self.assertEqual("incident-login", result.similarPastFailures[0].failureId)
        self.assertIn("module:jwt", result.impactedNodeIds)
        self.assertEqual(1, result.reasoningMetadata["historyRecordsCompared"])

    def test_ignores_history_from_another_repository(self) -> None:
        history = [
            HistoricalFailure(
                failureId="other-repo",
                repositoryId="repo-other",
                occurredAt="2026-07-17T10:30:00Z",
                failurePathNodeIds=["function:login"],
                confirmedRootCauseNodeIds=["function:login"],
            )
        ]
        result = BugLocalizer().localize(
            failure_graph(),
            FailureInput(repositoryId="repo-example", failingNodeId="function:login"),
            history,
        )

        self.assertEqual([], result.similarPastFailures)
        self.assertEqual(0, result.reasoningMetadata["historyRecordsCompared"])


if __name__ == "__main__":
    unittest.main()
