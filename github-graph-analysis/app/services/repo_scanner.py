from pathlib import Path

from app.schemas.responses import DirectoryMetadata, FileMetadata
from app.services.filesystem_extractor import extract_directories, extract_files
from app.services.static_code_extractor import ParsedRepository, extract_static_code


def scan_repository(root_path: str) -> tuple[list[DirectoryMetadata], list[FileMetadata], ParsedRepository]:
    root = Path(root_path).resolve()
    directories = extract_directories(root)
    files = extract_files(root)
    parsed = extract_static_code(root, files)
    return directories, files, parsed
