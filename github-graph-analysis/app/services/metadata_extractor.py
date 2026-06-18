from pathlib import Path


def detect_language(path: Path) -> str | None:
    extension_map = {
        ".java": "Java",
        ".py": "Python",
        ".ts": "TypeScript",
        ".tsx": "TypeScript",
        ".js": "JavaScript",
        ".jsx": "JavaScript"
    }
    return extension_map.get(path.suffix.lower())
