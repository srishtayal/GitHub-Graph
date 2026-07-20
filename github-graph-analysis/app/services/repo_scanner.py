from pathlib import Path

from app.core.config import AnalysisLimits
from app.schemas.responses import DirectoryMetadata, FileMetadata
from app.services.filesystem_extractor import extract_directories, extract_files
from app.services.static_code_extractor import ParsedRepository, extract_static_code


def scan_repository(root_path: str) -> tuple[list[DirectoryMetadata], list[FileMetadata], ParsedRepository]:
    root = Path(root_path).resolve()
    limits = AnalysisLimits.from_environment()
    directories = extract_directories(root)
    files = extract_files(root, limits.maxFiles)
    parsed = extract_static_code(root, files, limits.maxSourceFileBytes)
    return directories, files, parsed
