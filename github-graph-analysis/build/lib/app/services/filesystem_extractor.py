from hashlib import sha256
from pathlib import Path

from app.schemas.responses import DirectoryMetadata, FileMetadata
from app.services.language_detector import detect_language

SKIP_DIRS = {".git", "__pycache__", ".next", "node_modules", ".venv"}


def extract_directories(root: Path) -> list[DirectoryMetadata]:
    directories: list[DirectoryMetadata] = []

    for path in sorted(_iter_directories(root)):
        relative_path = path.relative_to(root).as_posix()
        parent = path.parent.relative_to(root).as_posix() if path.parent != root else None
        directories.append(
            DirectoryMetadata(
                relativePath=relative_path,
                name=path.name,
                parentPath=parent,
            )
        )

    return directories


def extract_files(root: Path) -> list[FileMetadata]:
    files: list[FileMetadata] = []

    for path in sorted(_iter_files(root)):
        relative_path = path.relative_to(root).as_posix()
        is_binary = _is_binary(path)
        files.append(
            FileMetadata(
                relativePath=relative_path,
                fileName=path.name,
                extension=path.suffix or None,
                language=detect_language(path),
                sizeBytes=path.stat().st_size,
                isBinary=is_binary,
            )
        )

    return files


def calculate_checksum(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _iter_directories(root: Path):
    for path in root.rglob("*"):
        if path.is_dir() and not _is_skipped(path, root):
            yield path


def _iter_files(root: Path):
    for path in root.rglob("*"):
        if path.is_file() and not _is_skipped(path, root):
            yield path


def _is_skipped(path: Path, root: Path) -> bool:
    try:
        relative_parts = path.relative_to(root).parts
    except ValueError:
        return True
    return any(part in SKIP_DIRS for part in relative_parts)


def _is_binary(path: Path) -> bool:
    try:
        with path.open("rb") as file_handle:
            chunk = file_handle.read(1024)
    except OSError:
        return True
    return b"\x00" in chunk
