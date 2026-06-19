from pathlib import Path

from app.parsers.python_parser import parse_python_file
from app.schemas.responses import FileMetadata, ImportMetadata, SymbolMetadata


def extract_python_symbols_and_imports(
    root: Path, files: list[FileMetadata]
) -> tuple[list[SymbolMetadata], list[ImportMetadata]]:
    symbols: list[SymbolMetadata] = []
    imports: list[ImportMetadata] = []

    for file_metadata in files:
        if file_metadata.language != "Python":
            continue

        path = root / file_metadata.relativePath
        file_symbols, file_imports = parse_python_file(path, file_metadata.relativePath)
        symbols.extend(file_symbols)
        imports.extend(file_imports)

    return symbols, imports
