import tempfile
import unittest
from pathlib import Path

from app.schemas.responses import FileMetadata
from app.services.static_code_extractor import extract_static_code


class StaticCodeExtractorTest(unittest.TestCase):
    def test_extracts_python_structure_and_relationships(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "models.py").write_text("class User:\n    pass\n", encoding="utf-8")
            (root / "api.py").write_text(
                """from .models import User
import jwt

router = APIRouter(prefix="/v1")

class Admin(User, Auditable):
    @router.post("/users")
    async def create_user(self, payload: dict, *args, **kwargs):
        token = jwt.encode(payload)
        return User.save(token)

app.add_api_route("/health", health, methods=["GET"])
urlpatterns = [path("items/", item_view)]
""",
                encoding="utf-8",
            )

            result = extract_static_code(
                root,
                [
                    _file("api.py", "Python"),
                    _file("models.py", "Python"),
                    _file("client.ts", "TypeScript"),
                ],
            )

        self.assertEqual(["api.py", "models.py"], [item.relativePath for item in result.code_files])
        admin = next(item for item in result.classes if item.name == "Admin")
        self.assertEqual(["User", "Auditable"], admin.bases)
        self.assertEqual(
            {("Admin", "User"), ("Admin", "Auditable")},
            {(item.childClass, item.parentClass) for item in result.inheritance},
        )

        create_user = next(item for item in result.functions if item.name == "create_user")
        self.assertEqual("Admin.create_user", create_user.qualifiedName)
        self.assertEqual("METHOD", create_user.functionType)
        self.assertTrue(create_user.isAsync)
        self.assertEqual(["self", "payload", "*args", "**kwargs"], create_user.parameters)

        calls = {(item.expression, item.caller) for item in result.method_calls}
        self.assertIn(("jwt.encode", "Admin.create_user"), calls)
        self.assertIn(("User.save", "Admin.create_user"), calls)

        routes = {(item.framework, item.httpMethod, item.path, item.handler) for item in result.api_routes}
        self.assertIn(("FastAPI/Flask", "POST", "/v1/users", "Admin.create_user"), routes)
        self.assertIn(("FastAPI", "GET", "/health", "health"), routes)
        self.assertIn(("Django", "ANY", "items/", "item_view"), routes)

        dependencies = {(item.targetModule, item.resolvedPath, item.dependencyType) for item in result.module_dependencies}
        self.assertIn((".models.User", "models.py", "INTERNAL"), dependencies)
        self.assertIn(("jwt", None, "EXTERNAL"), dependencies)

    def test_resolves_absolute_imports_in_src_layout(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "src" / "store").mkdir(parents=True)
            (root / "src" / "store" / "models.py").write_text("class Item:\n    pass\n", encoding="utf-8")
            (root / "src" / "service.py").write_text("from store.models import Item\n", encoding="utf-8")
            result = extract_static_code(
                root,
                [_file("src/service.py", "Python"), _file("src/store/models.py", "Python")],
            )

        dependency = next(item for item in result.module_dependencies if item.targetModule == "store.models.Item")
        self.assertEqual("src/store/models.py", dependency.resolvedPath)
        self.assertEqual("INTERNAL", dependency.dependencyType)

    def test_invalid_python_file_is_skipped_without_failing_scan(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "broken.py").write_text("def broken(:\n", encoding="utf-8")
            result = extract_static_code(root, [_file("broken.py", "Python")])

        self.assertEqual([], result.code_files)
        self.assertEqual([], result.symbols)


def _file(relative_path: str, language: str) -> FileMetadata:
    path = Path(relative_path)
    return FileMetadata(
        relativePath=relative_path,
        fileName=path.name,
        extension=path.suffix,
        language=language,
        sizeBytes=0,
        isBinary=False,
    )


if __name__ == "__main__":
    unittest.main()
