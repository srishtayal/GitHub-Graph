from pathlib import Path


def scan_repository(local_path: str) -> list[Path]:
    return [path for path in Path(local_path).rglob("*")]
