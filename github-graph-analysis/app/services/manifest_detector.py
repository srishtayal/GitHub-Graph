from pathlib import Path


KNOWN_MANIFESTS = {
    "pom.xml": "maven-pom",
    "package.json": "npm-package",
    "pyproject.toml": "python-project"
}


def detect_manifest(path: Path) -> str | None:
    return KNOWN_MANIFESTS.get(path.name)
