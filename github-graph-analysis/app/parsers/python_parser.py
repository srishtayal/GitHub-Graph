import ast
from pathlib import Path

from app.schemas.responses import (
    ApiRouteMetadata,
    ClassMetadata,
    CodeFileMetadata,
    FunctionMetadata,
    ImportMetadata,
    InheritanceMetadata,
    MethodCallMetadata,
    SymbolMetadata,
)


HTTP_METHODS = {"delete", "get", "head", "options", "patch", "post", "put", "websocket"}


class PythonCodeVisitor(ast.NodeVisitor):
    def __init__(self, relative_path: str):
        self.relative_path = relative_path
        self.class_stack: list[str] = []
        self.function_stack: list[str] = []
        self.classes: list[ClassMetadata] = []
        self.functions: list[FunctionMetadata] = []
        self.imports: list[ImportMetadata] = []
        self.method_calls: list[MethodCallMetadata] = []
        self.inheritance: list[InheritanceMetadata] = []
        self.api_routes: list[ApiRouteMetadata] = []
        self.route_prefixes: dict[str, str] = {}

    def visit_Assign(self, node: ast.Assign) -> None:
        if isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Name):
            if node.value.func.id in {"APIRouter", "Blueprint"}:
                keyword_name = "prefix" if node.value.func.id == "APIRouter" else "url_prefix"
                keyword = next((item for item in node.value.keywords if item.arg == keyword_name), None)
                if keyword and isinstance(keyword.value, ast.Constant) and isinstance(keyword.value.value, str):
                    for target in node.targets:
                        if isinstance(target, ast.Name):
                            self.route_prefixes[target.id] = keyword.value.value
        self.generic_visit(node)

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        qualified_name = ".".join([*self.class_stack, node.name])
        bases = [_safe_unparse(base) for base in node.bases]
        self.classes.append(
            ClassMetadata(
                relativePath=self.relative_path,
                name=node.name,
                qualifiedName=qualified_name,
                bases=bases,
                startLine=node.lineno,
                endLine=getattr(node, "end_lineno", node.lineno),
            )
        )
        self.inheritance.extend(
            InheritanceMetadata(
                relativePath=self.relative_path,
                childClass=qualified_name,
                parentClass=base,
                startLine=node.lineno,
            )
            for base in bases
        )
        self.class_stack.append(node.name)
        self.generic_visit(node)
        self.class_stack.pop()

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self._record_function(node, is_async=False)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self._record_function(node, is_async=True)

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
        module = f"{'.' * node.level}{node.module or ''}"
        for alias in node.names:
            separator = "" if module.endswith(".") else "."
            self.imports.append(
                ImportMetadata(
                    relativePath=self.relative_path,
                    importValue=f"{module}{separator}{alias.name}" if module else alias.name,
                    importType="FROM_IMPORT",
                    resolvedPath=None,
                )
            )

    def visit_Call(self, node: ast.Call) -> None:
        expression = _safe_unparse(node.func)
        if isinstance(node.func, ast.Attribute):
            name = node.func.attr
            receiver = _safe_unparse(node.func.value)
        elif isinstance(node.func, ast.Name):
            name = node.func.id
            receiver = None
        else:
            name = expression
            receiver = None

        self.method_calls.append(
            MethodCallMetadata(
                relativePath=self.relative_path,
                caller=".".join([*self.class_stack, *self.function_stack]) if self.function_stack else None,
                name=name,
                receiver=receiver,
                expression=expression,
                startLine=node.lineno,
            )
        )
        self._record_imperative_route(node, name, receiver)
        self.generic_visit(node)

    def _record_function(self, node: ast.FunctionDef | ast.AsyncFunctionDef, is_async: bool) -> None:
        is_method = bool(self.class_stack) and not self.function_stack
        parent_class = self.class_stack[-1] if is_method else None
        qualified_name = ".".join([*self.class_stack, *self.function_stack, node.name])
        self.functions.append(
            FunctionMetadata(
                relativePath=self.relative_path,
                name=node.name,
                qualifiedName=qualified_name,
                functionType="METHOD" if is_method else "FUNCTION",
                parentClass=parent_class,
                parameters=_parameters(node.args),
                isAsync=is_async,
                startLine=node.lineno,
                endLine=getattr(node, "end_lineno", node.lineno),
            )
        )
        self._record_decorator_routes(node, qualified_name)
        self.function_stack.append(node.name)
        self.generic_visit(node)
        self.function_stack.pop()

    def _record_decorator_routes(
        self, node: ast.FunctionDef | ast.AsyncFunctionDef, handler: str
    ) -> None:
        for decorator in node.decorator_list:
            if not isinstance(decorator, ast.Call) or not isinstance(decorator.func, ast.Attribute):
                continue
            method = decorator.func.attr.lower()
            if method not in HTTP_METHODS | {"api_route", "route"}:
                continue
            receiver = _safe_unparse(decorator.func.value)
            path = _join_route(self.route_prefixes.get(receiver, ""), _string_argument(decorator, 0) or "/")
            methods = _methods_keyword(decorator)
            if method in HTTP_METHODS:
                methods = [method.upper()]
            for http_method in methods or ["ANY"]:
                self.api_routes.append(
                    ApiRouteMetadata(
                        relativePath=self.relative_path,
                        framework="FastAPI/Flask",
                        httpMethod=http_method,
                        path=path,
                        handler=handler,
                        startLine=getattr(decorator, "lineno", node.lineno),
                    )
                )

    def _record_imperative_route(self, node: ast.Call, name: str, receiver: str | None) -> None:
        if name == "add_api_route" and receiver:
            path = _string_argument(node, 0)
            if path is None:
                return
            handler = _safe_unparse(node.args[1]) if len(node.args) > 1 else "unknown"
            for method in _methods_keyword(node) or ["ANY"]:
                self.api_routes.append(
                    ApiRouteMetadata(
                        relativePath=self.relative_path,
                        framework="FastAPI",
                        httpMethod=method,
                        path=path,
                        handler=handler,
                        startLine=node.lineno,
                    )
                )
        elif name in {"path", "re_path"} and receiver is None:
            path = _string_argument(node, 0)
            if path is None:
                return
            handler = _safe_unparse(node.args[1]) if len(node.args) > 1 else "unknown"
            self.api_routes.append(
                ApiRouteMetadata(
                    relativePath=self.relative_path,
                    framework="Django",
                    httpMethod="ANY",
                    path=path,
                    handler=handler,
                    startLine=node.lineno,
                )
            )


def parse_python_code_file(path: Path, relative_path: str) -> CodeFileMetadata | None:
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        source = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return None

    try:
        tree = ast.parse(source)
    except SyntaxError:
        return None

    visitor = PythonCodeVisitor(relative_path)
    visitor.visit(tree)
    return CodeFileMetadata(
        relativePath=relative_path,
        language="Python",
        classes=visitor.classes,
        functions=visitor.functions,
        imports=_dedupe_imports(visitor.imports),
        methodCalls=visitor.method_calls,
        inheritance=visitor.inheritance,
        apiRoutes=_dedupe_routes(visitor.api_routes),
        moduleDependencies=[],
    )


def parse_python_file(path: Path, relative_path: str) -> tuple[list[SymbolMetadata], list[ImportMetadata]]:
    code_file = parse_python_code_file(path, relative_path)
    if code_file is None:
        return [], []
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
    return symbols, code_file.imports


def _parameters(arguments: ast.arguments) -> list[str]:
    parameters = [item.arg for item in [*arguments.posonlyargs, *arguments.args]]
    if arguments.vararg:
        parameters.append(f"*{arguments.vararg.arg}")
    parameters.extend(item.arg for item in arguments.kwonlyargs)
    if arguments.kwarg:
        parameters.append(f"**{arguments.kwarg.arg}")
    return parameters


def _safe_unparse(node: ast.AST) -> str:
    try:
        return ast.unparse(node)
    except (TypeError, ValueError):
        return "unknown"


def _string_argument(node: ast.Call, index: int) -> str | None:
    if len(node.args) <= index:
        return None
    value = node.args[index]
    return value.value if isinstance(value, ast.Constant) and isinstance(value.value, str) else None


def _methods_keyword(node: ast.Call) -> list[str]:
    keyword = next((item for item in node.keywords if item.arg == "methods"), None)
    if keyword is None or not isinstance(keyword.value, (ast.List, ast.Tuple, ast.Set)):
        return []
    return [
        item.value.upper()
        for item in keyword.value.elts
        if isinstance(item, ast.Constant) and isinstance(item.value, str)
    ]


def _join_route(prefix: str, path: str) -> str:
    joined = "/".join(item.strip("/") for item in (prefix, path) if item.strip("/"))
    return f"/{joined}" if joined else "/"


def _dedupe_imports(imports: list[ImportMetadata]) -> list[ImportMetadata]:
    seen: set[tuple[str, str | None]] = set()
    result: list[ImportMetadata] = []
    for item in imports:
        key = (item.importValue, item.importType)
        if key not in seen:
            seen.add(key)
            result.append(item)
    return result


def _dedupe_routes(routes: list[ApiRouteMetadata]) -> list[ApiRouteMetadata]:
    seen: set[tuple[str, str, str, str]] = set()
    result: list[ApiRouteMetadata] = []
    for item in routes:
        key = (item.framework, item.httpMethod, item.path, item.handler)
        if key not in seen:
            seen.add(key)
            result.append(item)
    return result
