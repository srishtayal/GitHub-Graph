import unittest
from pathlib import Path

from app.services.failure_history_store import JsonFailureHistoryStore


class JsonFailureHistoryStoreTest(unittest.TestCase):
    def test_returns_only_records_for_requested_repository(self) -> None:
        fixture_path = Path(__file__).parent / "fixtures" / "phase6_failures.json"

        records = JsonFailureHistoryStore(fixture_path).list_for_repository("repo-example")

        self.assertEqual(["incident-auth-token"], [record.failureId for record in records])
        self.assertEqual("ValueError", records[0].errorSignature.exceptionType)
        self.assertEqual(["function:authenticate"], records[0].confirmedRootCauseNodeIds)

    def test_returns_empty_list_when_repository_has_no_history(self) -> None:
        fixture_path = Path(__file__).parent / "fixtures" / "phase6_failures.json"

        records = JsonFailureHistoryStore(fixture_path).list_for_repository("repo-missing")

        self.assertEqual([], records)


if __name__ == "__main__":
    unittest.main()
