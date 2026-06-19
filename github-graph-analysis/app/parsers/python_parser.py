import ast
from pathlib import Path

from app.schemas.responses import ImportMetadata, SymbolMetadata


class PythonSymbolVisitor(ast.NodeVisitor):
    def __init__(self, relative_path: str):
        self.relative_path = relative_path
        self.class_stack: list[str] = []
        self.symbols: list[SymbolMetadata] = []
        self.imports: list[ImportMetadata] = []

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        qualified_name = ".".join([*self.class_stack, node.name]) if self.class_stack else node.name
        parent_symbol_name = self.class_stack[-1] if self.class_stack else None
        self.symbols.append(
            SymbolMetadata(
                relativePath=self.relative_path,
                symbolType="CLASS",
                name=node.name,
                qualifiedName=qualified_name,
                language="Python",
                startLine=node.lineno,
                endLine=getattr(node, "end_lineno", node.lineno),
                parentSymbolName=parent_symbol_name,
            )
        )
        self.class_stack.append(node.name)
        self.generic_visit(node)
        self.class_stack.pop()

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self._record_function(node)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self._record_function(node)

    def visit_Import(self, node: ast.Import) -> None:
        for alias in node.names:
            self.imports.append(
                ImportMetadata(
                    relativePath=self.relative_path,
                    importValue=alias.name,
                    importType="MODULE",
                    resolvedPath=None,
                )
            )

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
        module = node.module or ""
        for alias in node.names:
            value = f"{module}.{alias.name}" if module else alias.name
            self.imports.append(
                ImportMetadata(
                    relativePath=self.relative_path,
                    importValue=value,
                    importType="FROM_IMPORT",
                    resolvedPath=None,
                )
            )

    def _record_function(self, node: ast.FunctionDef | ast.AsyncFunctionDef) -> None:
        parent_symbol_name = self.class_stack[-1] if self.class_stack else None
        symbol_type = "METHOD" if parent_symbol_name else "FUNCTION"
        qualified_name_parts = [*self.class_stack, node.name]
        self.symbols.append(
            SymbolMetadata(
                relativePath=self.relative_path,
                symbolType=symbol_type,
                name=node.name,
                qualifiedName=".".join(qualified_name_parts),
                language="Python",
                startLine=node.lineno,
                endLine=getattr(node, "end_lineno", node.lineno),
                parentSymbolName=parent_symbol_name,
            )
        )
        self.generic_visit(node)


def parse_python_file(path: Path, relative_path: str) -> tuple[list[SymbolMetadata], list[ImportMetadata]]:
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        source = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return [], []

    try:
        tree = ast.parse(source)
    except SyntaxError:
        return [], []

    visitor = PythonSymbolVisitor(relative_path)
    visitor.visit(tree)
    return visitor.symbols, visitor.imports
