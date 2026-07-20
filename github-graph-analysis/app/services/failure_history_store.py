import json
from pathlib import Path
from typing import Protocol

from pydantic import TypeAdapter

from app.schemas.failure_analysis import HistoricalFailure


class FailureHistoryStore(Protocol):
    def list_for_repository(self, repository_id: str) -> list[HistoricalFailure]: ...


class JsonFailureHistoryStore:
    """Test-fixture adapter; runtime history is supplied by Spring from PostgreSQL."""

    def __init__(self, fixture_path: Path) -> None:
        self.fixture_path = fixture_path

    def list_for_repository(self, repository_id: str) -> list[HistoricalFailure]:
        with self.fixture_path.open(encoding="utf-8") as fixture_file:
            raw_records = json.load(fixture_file)
        records = TypeAdapter(list[HistoricalFailure]).validate_python(raw_records)
        return [record for record in records if record.repositoryId == repository_id]
