from pathlib import Path

from app.schemas.responses import DirectoryMetadata, FileMetadata, ImportMetadata, SymbolMetadata
from app.services.filesystem_extractor import extract_directories, extract_files
from app.services.symbol_extractor import extract_python_symbols_and_imports


def scan_repository(root_path: str) -> tuple[
    list[DirectoryMetadata],
    list[FileMetadata],
    list[SymbolMetadata],
    list[ImportMetadata],
]:
    root = Path(root_path).resolve()
    directories = extract_directories(root)
    files = extract_files(root)
    symbols, imports = extract_python_symbols_and_imports(root, files)
    return directories, files, symbols, imports
