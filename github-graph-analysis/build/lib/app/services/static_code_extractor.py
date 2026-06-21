from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Iterable

from app.parsers.python_parser import parse_python_code_file
from app.schemas.responses import (
    ApiRouteMetadata,
    ClassMetadata,
    CodeFileMetadata,
    FileMetadata,
    FunctionMetadata,
    ImportMetadata,
    InheritanceMetadata,
    MethodCallMetadata,
    ModuleDependencyMetadata,
    SymbolMetadata,
)


@dataclass
class ParsedRepository:
    code_files: list[CodeFileMetadata] = field(default_factory=list)
    symbols: list[SymbolMetadata] = field(default_factory=list)
    imports: list[ImportMetadata] = field(default_factory=list)
    classes: list[ClassMetadata] = field(default_factory=list)
    functions: list[FunctionMetadata] = field(default_factory=list)
    method_calls: list[MethodCallMetadata] = field(default_factory=list)
    inheritance: list[InheritanceMetadata] = field(default_factory=list)
    api_routes: list[ApiRouteMetadata] = field(default_factory=list)
    module_dependencies: list[ModuleDependencyMetadata] = field(default_factory=list)


def extract_static_code(root: Path, files: Iterable[FileMetadata]) -> ParsedRepository:
    parsed = ParsedRepository()
    for file_metadata in files:
        if file_metadata.language != "Python" or file_metadata.isBinary:
            continue
        code_file = parse_python_code_file(root / file_metadata.relativePath, file_metadata.relativePath)
        if code_file is not None:
            parsed.code_files.append(code_file)

    _resolve_dependencies(parsed.code_files)
    for code_file in parsed.code_files:
        parsed.classes.extend(code_file.classes)
        parsed.functions.extend(code_file.functions)
        parsed.imports.extend(code_file.imports)
        parsed.method_calls.extend(code_file.methodCalls)
        parsed.inheritance.extend(code_file.inheritance)
        parsed.api_routes.extend(code_file.apiRoutes)
        parsed.module_dependencies.extend(code_file.moduleDependencies)
        parsed.symbols.extend(_to_symbols(code_file))
    return parsed


def _resolve_dependencies(code_files: list[CodeFileMetadata]) -> None:
    known_paths = {item.relativePath for item in code_files}
    for code_file in code_files:
        dependencies: list[ModuleDependencyMetadata] = []
        seen: set[tuple[str, str | None]] = set()
        for item in code_file.imports:
            resolved_path = _resolve_python_import(code_file.relativePath, item.importValue, known_paths)
            item.resolvedPath = resolved_path
            key = (item.importValue, resolved_path)
            if key in seen:
                continue
            seen.add(key)
            dependencies.append(
                ModuleDependencyMetadata(
                    sourcePath=code_file.relativePath,
                    targetModule=item.importValue,
                    resolvedPath=resolved_path,
                    dependencyType="INTERNAL" if resolved_path else "EXTERNAL",
                )
            )
        code_file.moduleDependencies = dependencies


def _resolve_python_import(source_path: str, import_value: str, known_paths: set[str]) -> str | None:
    leading_dots = len(import_value) - len(import_value.lstrip("."))
    module = import_value.lstrip(".")
    base = PurePosixPath(source_path).parent
    if leading_dots == 0:
        base = PurePosixPath()
    else:
        for _ in range(leading_dots - 1):
            base = base.parent

    parts = module.split(".") if module else []
    for end in range(len(parts), 0, -1):
        candidate = base.joinpath(*parts[:end])
        for path in (f"{candidate}.py", f"{candidate}/__init__.py"):
            matches = [known for known in known_paths if known == path or known.endswith(f"/{path}")]
            if matches:
                return min(matches, key=len)
    return None


def _to_symbols(code_file: CodeFileMetadata) -> list[SymbolMetadata]:
    symbols = [
        SymbolMetadata(
            relativePath=item.relativePath,
            symbolType="CLASS",
            name=item.name,
            qualifiedName=item.qualifiedName,
            language="Python",
            startLine=item.startLine,
            endLine=item.endLine,
            parentSymbolName=None,
        )
        for item in code_file.classes
    ]
    symbols.extend(
        SymbolMetadata(
            relativePath=item.relativePath,
            symbolType=item.functionType,
            name=item.name,
            qualifiedName=item.qualifiedName,
            language="Python",
            startLine=item.startLine,
            endLine=item.endLine,
            parentSymbolName=item.parentClass,
        )
        for item in code_file.functions
    )
    return symbols
